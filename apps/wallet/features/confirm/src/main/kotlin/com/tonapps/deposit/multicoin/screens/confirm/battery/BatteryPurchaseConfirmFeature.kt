package com.tonapps.deposit.multicoin.screens.confirm.battery

import com.tonapps.async.Async
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.MnemonicWallet
import com.tonapps.deposit.multicoin.screens.confirm.fee.GaslessRelayer
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerQuoteExpiredException
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerRejectedException
import com.tonapps.deposit.multicoin.screens.confirm.fee.RelayerOutcomeUnknownException
import com.tonapps.log.L
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.mvi.MviRelay
import com.tonapps.time.TimeProvider
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.passcode.PasscodeReason
import com.tonapps.wallet.data.passcode.WalletUnlocker
import io.infrastructure.ClientException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class BatteryPurchaseSendingState {
    None, Loading, Done
}

sealed interface BatteryPurchaseError {

    /** The battery refused the purchase, with its own words when it gave some. */
    data class Rejected(val message: String?) : BatteryPurchaseError

    /** The quote ran out before it was sent: the screen quotes it again. */
    data object Expired : BatteryPurchaseError

    /** A new quote adds fewer charges than the one the user swiped for: the screen shows it to swipe again. */
    data object ChargesChanged : BatteryPurchaseError

    /** The network fee takes the whole purchase: nothing would be added, so there is nothing to swipe for. */
    data object NoCharges : BatteryPurchaseError

    /** The request may have reached the battery: sending again could pay twice. */
    data object OutcomeUnknown : BatteryPurchaseError

    data object Failed : BatteryPurchaseError

    val isRetryable: Boolean
        get() = this != OutcomeUnknown && this != NoCharges
}

sealed interface BatteryPurchaseEvent {
    data object Sent : BatteryPurchaseEvent
}

/**
 * Confirm feature for [ConfirmType.BatteryPurchase]: quotes the purchase when the screen opens and again
 * shortly before the quote runs out. On the swipe, once the passcode is in, it quotes once more and signs
 * that quote's operation, which it sends to `/gasless/send`. The battery balance grows once the transfer
 * lands: the screen that opened this one waits for it.
 */
class BatteryPurchaseConfirmFeature(
    private val request: ConfirmRequest,
    private val purchaser: BatteryPurchaser,
    private val accountRepo: McAccountRepository,
    private val walletUnlocker: WalletUnlocker,
    private val timeProvider: TimeProvider,
) : AsyncViewModel() {

    val purchase = request.type as ConfirmType.BatteryPurchase

    private val relay = MviRelay<BatteryPurchaseEvent>()
    val events = relay.events

    // The last quote stays on screen while a new one loads or fails, so the body does not blink.
    private val reviewState = MutableStateFlow<BatteryPurchaseReview?>(null)
    private val accountState = MutableStateFlow<AccountWithDetails?>(null)
    private val loadingState = MutableStateFlow(true)
    private val loadFailure = MutableStateFlow<BatteryPurchaseError?>(null)
    private val sendingState = MutableStateFlow(BatteryPurchaseSendingState.None)
    private val submitFailure = MutableStateFlow<BatteryPurchaseError?>(null)

    val review: StateFlow<BatteryPurchaseReview?> = reviewState
    val account: StateFlow<AccountWithDetails?> = accountState
    val loading: StateFlow<Boolean> = loadingState
    val loadError: StateFlow<BatteryPurchaseError?> = loadFailure
    val sending: StateFlow<BatteryPurchaseSendingState> = sendingState
    val submitError: StateFlow<BatteryPurchaseError?> = submitFailure

    private var loadJob: Job? = null

    init {
        stateScope.launch { accountState.value = findAccount() }
        load()
    }

    fun confirm() {
        mainScope.launch { submit() }
    }

    fun retry() {
        when (submitFailure.value) {
            // It may have been paid already: another send could pay twice.
            BatteryPurchaseError.OutcomeUnknown -> return
            // The new quote is on screen: clearing the error brings the slider back for a swipe on it.
            BatteryPurchaseError.ChargesChanged -> {
                submitFailure.value = null
                return
            }
            else -> Unit
        }

        if (loadFailure.value != null || submitFailure.value == BatteryPurchaseError.Expired) {
            submitFailure.value = null
            load()
        } else {
            confirm()
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = stateScope.launch { quoteAndShow(background = false) }
    }

    /**
     * Quotes the purchase and shows it. A [background] requote keeps a quote that is still on screen and valid:
     * the slider stays live, and a failure is retried rather than shown until that quote runs out.
     */
    private suspend fun quoteAndShow(background: Boolean) {
        val keepsShown = background && reviewState.value?.isExpired(timeProvider.currentTimeMillis()) == false
        if (!keepsShown) {
            loadingState.value = true
        }

        val loaded = try {
            withContext(Async.Io) { purchaser.quote(request) }
        } catch (e: CancellationException) {
            loadingState.value = false
            throw e
        } catch (e: Throwable) {
            L.e(e)
            val shown = reviewState.value
            if (background && shown != null && !shown.isExpired(timeProvider.currentTimeMillis())) {
                delay(MIN_REQUOTE_DELAY_MS)
                sendingState.first { it == BatteryPurchaseSendingState.None }
                quoteAndShow(background = true)
                return
            }

            loadFailure.value = e.toPurchaseError()
            loadingState.value = false
            return
        }

        // A swipe that started meanwhile signs a quote of its own: this one waits for it to be over, and a
        // purchase that went out leaves the screen as it was sent.
        sendingState.first { it == BatteryPurchaseSendingState.None }

        present(loaded)
        requoteBeforeExpiry(loaded)
    }

    private fun show(requoted: BatteryPurchaseReview) {
        loadJob?.cancel()
        present(requoted)
        loadJob = stateScope.launch { requoteBeforeExpiry(requoted) }
    }

    // A quote that adds no charges is a load error: it disables the slider until a quote with charges replaces it.
    private fun present(review: BatteryPurchaseReview) {
        reviewState.value = review
        // The quote on screen replaces the one that expired: it can be swiped as it is.
        if (submitFailure.value == BatteryPurchaseError.Expired) {
            submitFailure.value = null
        }
        loadFailure.value = if (review.quote.charges <= 0) {
            BatteryPurchaseError.NoCharges
        } else {
            null
        }
        loadingState.value = false
    }

    private suspend fun requoteBeforeExpiry(review: BatteryPurchaseReview) {
        // A quote that lives shorter than the lead is not asked for again and again.
        delay((review.expiresAt - timeProvider.currentTimeMillis() - REQUOTE_LEAD_MS).coerceAtLeast(MIN_REQUOTE_DELAY_MS))

        // A send in flight owns this quote; quote again once it is over, if the screen is still here.
        sendingState.first { it == BatteryPurchaseSendingState.None }
        quoteAndShow(background = true)
    }

    private suspend fun submit() {
        if (sendingState.value != BatteryPurchaseSendingState.None || loadingState.value || loadFailure.value != null) {
            return
        }

        // A send that may have gone out is never repeated from this screen.
        if (submitFailure.value?.isRetryable == false) {
            return
        }

        val shown = reviewState.value ?: return
        sendingState.value = BatteryPurchaseSendingState.Loading
        submitFailure.value = null

        try {
            val account = accountState.value ?: findAccount() ?: throw IllegalStateException("No account for ${request.data.assetId}")
            val cryptoWallet = walletUnlocker.unlockWallet(request.data.walletId, PasscodeReason.TOPUP_BATTERY)

            // Sign a quote made now, unless it adds less than was shown.
            val signing = withContext(Async.Io) { fresh(shown) }

            // A quote that adds nothing is on screen as a load error by now: nothing to sign.
            if (signing.quote.charges <= 0) {
                sendingState.value = BatteryPurchaseSendingState.None
                return
            }

            sendOnce(signing, account, cryptoWallet)
        } catch (e: CancellationException) {
            sendingState.value = BatteryPurchaseSendingState.None
            // The user closed the passcode prompt: nothing was unlocked or signed. Only a closed screen
            // cancels further; otherwise the swipe is back and the next one asks for the passcode again.
            currentCoroutineContext().ensureActive()
        } catch (e: Throwable) {
            L.e(e)
            // The error is set before the send is released: a requote waiting on the release sees it and,
            // when it replaces an expired quote, clears it.
            submitFailure.value = e.toPurchaseError()
            sendingState.value = BatteryPurchaseSendingState.None
        }
    }

    /**
     * Sends [signing] and reports it. Once it is sent, closing the screen must not lose the news: the recharge
     * screen refreshes on it, so this part is not cancelled. A cancellation the screen did not cause (a timeout
     * below) leaves the outcome unknown rather than bringing the slider back.
     */
    private suspend fun sendOnce(signing: BatteryPurchaseReview, account: AccountWithDetails, cryptoWallet: MnemonicWallet) {
        try {
            withContext(NonCancellable) {
                withContext(Async.Io) { purchaser.send(request.data.walletId, signing, account, cryptoWallet) }
                sendingState.value = BatteryPurchaseSendingState.Done
                delay(DONE_DELAY_MS)
                relay.emit(BatteryPurchaseEvent.Sent)
            }
        } catch (e: CancellationException) {
            if (!currentCoroutineContext().isActive) {
                throw e
            }
            throw RelayerOutcomeUnknownException(e)
        }
    }

    /**
     * A new quote of the purchase, put on screen in place of [shown]. It is asked for after every passcode: a
     * Solana transaction carries a recent blockhash, which lapses well before the quote's `expires_at`, so the
     * one shown may no longer land even while its deadline has not passed.
     */
    private suspend fun fresh(shown: BatteryPurchaseReview): BatteryPurchaseReview {
        val requoted = purchaser.quote(request)
        show(requoted)

        // A quote that adds nothing is the load error show() put up, not a changed price to swipe for again.
        if (requoted.quote.charges in 1 until shown.quote.charges) {
            throw ChargesChangedException()
        }
        return requoted
    }

    private suspend fun findAccount(): AccountWithDetails? {
        return try {
            accountRepo.findAccount(request.data.walletId, request.data.assetId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
            null
        }
    }

    private companion object {
        const val REQUOTE_LEAD_MS = 10_000L
        const val MIN_REQUOTE_DELAY_MS = 3_000L
        const val DONE_DELAY_MS = 500L
    }
}

/** A new quote adds fewer charges than the one the user swiped for. */
internal class ChargesChangedException : Exception("the new quote adds fewer charges")


internal fun Throwable.toPurchaseError(): BatteryPurchaseError = when (this) {
    is RelayerOutcomeUnknownException -> BatteryPurchaseError.OutcomeUnknown
    is RelayerQuoteExpiredException -> BatteryPurchaseError.Expired
    is RelayerRejectedException -> if (nothingSent) {
        BatteryPurchaseError.Rejected(reason)
    } else {
        BatteryPurchaseError.OutcomeUnknown
    }

    is ClientException -> {
        val rejection = GaslessRelayer.rejection(this)
        if (rejection.isExpired) {
            BatteryPurchaseError.Expired
        } else {
            BatteryPurchaseError.Rejected(rejection.message)
        }
    }

    is ChargesChangedException -> BatteryPurchaseError.ChargesChanged
    else -> BatteryPurchaseError.Failed
}
