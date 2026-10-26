package com.tonapps.deposit.multicoin.screens.confirm.perpetual

import android.content.Context
import com.tonapps.async.Async
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.log.L
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.mvi.MviRelay
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.passcode.PasscodeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class PerpetualSendingState {
    None, Loading, Done
}

sealed interface PerpetualConfirmEvent {
    data object Submitted : PerpetualConfirmEvent
}

/**
 * Confirm feature for [ConfirmType.PerpetualWithdraw]. Quotes through [PerpetualConfirmDelegate]
 * when the screen opens, quotes again shortly before the quote expires, and submits on swipe.
 */
class PerpetualConfirmFeature(
    private val request: ConfirmRequest,
    private val delegate: PerpetualConfirmDelegate,
    private val accountRepo: McAccountRepository,
    private val passcodeManager: PasscodeManager,
) : AsyncViewModel() {

    private val withdraw = request.type as ConfirmType.PerpetualWithdraw

    private val relay = MviRelay<PerpetualConfirmEvent>()
    val events = relay.events

    // The last review stays on screen while a re-quote loads or fails, so the body does not blink.
    private val reviewState = MutableStateFlow<PerpetualReview?>(null)
    private val loadingState = MutableStateFlow(true)
    private val loadFailure = MutableStateFlow<PerpetualConfirmError?>(null)
    private val sendingState = MutableStateFlow(PerpetualSendingState.None)
    private val submitFailure = MutableStateFlow<PerpetualConfirmError?>(null)

    val review: StateFlow<PerpetualReview?> = reviewState
    val loading: StateFlow<Boolean> = loadingState
    val loadError: StateFlow<PerpetualConfirmError?> = loadFailure
    val sending: StateFlow<PerpetualSendingState> = sendingState
    val submitError: StateFlow<PerpetualConfirmError?> = submitFailure

    private var loadJob: Job? = null

    init {
        load()
    }

    fun confirm(context: Context) {
        mainScope.launch { submit(context) }
    }

    fun retry(context: Context) {
        if (loadFailure.value != null) {
            load()
        } else {
            confirm(context)
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = stateScope.launch {
            loadingState.value = true
            val loaded = try {
                withContext(Async.Io) { delegate.review(request, withdraw) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                L.e(e)
                loadFailure.value = e.toConfirmError()
                loadingState.value = false
                return@launch
            }
            reviewState.value = loaded
            loadFailure.value = null
            loadingState.value = false

            val expiresAt = loaded.expiresAtMs ?: return@launch
            delay((expiresAt - System.currentTimeMillis() - REQUOTE_LEAD_MS).coerceAtLeast(0L))
            // A send in flight owns this quote; re-quote once it is over, if the screen is still here.
            sendingState.first { it == PerpetualSendingState.None }
            load()
        }
    }

    private suspend fun submit(context: Context) {
        if (sendingState.value != PerpetualSendingState.None || loadingState.value || loadFailure.value != null) {
            return
        }
        val loaded = reviewState.value ?: return
        sendingState.value = PerpetualSendingState.Loading
        submitFailure.value = null
        try {
            val expiresAt = loaded.expiresAtMs
            if (expiresAt != null && expiresAt < System.currentTimeMillis() + REQUOTE_LEAD_MS) {
                throw PerpetualConfirmException(PerpetualConfirmError.QuoteExpired, requote = true)
            }
            val cryptoWallet = unlockCryptoWallet(context, loaded.walletId)
            withContext(Async.Io) { delegate.submit(loaded, cryptoWallet) }
            sendingState.value = PerpetualSendingState.Done
            delay(DONE_DELAY_MS)
            relay.emit(PerpetualConfirmEvent.Submitted)
        } catch (e: CancellationException) {
            sendingState.value = PerpetualSendingState.None
            // The user closed the passcode prompt: nothing was unlocked or signed. Only a closed screen
            // cancels further; otherwise the swipe is back and the next one asks for the passcode again.
            currentCoroutineContext().ensureActive()
        } catch (e: Throwable) {
            L.e(e)
            sendingState.value = PerpetualSendingState.None
            submitFailure.value = e.toConfirmError()
            if (e is PerpetualConfirmException && e.requote) {
                load()
            }
        }
    }

    private suspend fun unlockCryptoWallet(context: Context, walletId: String): CryptoWallet {
        return passcodeManager.unlockMultichainVault(context) { coder ->
            accountRepo.getCryptoWallet(walletId, coder)
                ?: throw IllegalStateException("No multichain wallet for $walletId")
        }
    }

    private fun Throwable.toConfirmError(): PerpetualConfirmError {
        return (this as? PerpetualConfirmException)?.error ?: PerpetualConfirmError.Failed
    }

    private companion object {
        const val REQUOTE_LEAD_MS = 2_000L
        const val DONE_DELAY_MS = 500L
    }
}
