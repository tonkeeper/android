package com.tonapps.deposit.multicoin.screens.confirm.perpetual

import com.tonapps.async.Async
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.log.L
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.mvi.MviRelay
import com.tonapps.time.TimeProvider
import com.tonapps.wallet.data.passcode.PasscodeReason
import com.tonapps.wallet.data.passcode.WalletUnlocker
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
    private val walletUnlocker: WalletUnlocker,
    private val timeProvider: TimeProvider,
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

    fun confirm() {
        mainScope.launch { submit() }
    }

    fun retry() {
        if (loadFailure.value != null) {
            load()
        } else {
            confirm()
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
            requoteBeforeExpiry(loaded)
        }
    }

    private fun show(requoted: PerpetualReview) {
        loadJob?.cancel()
        reviewState.value = requoted
        loadJob = stateScope.launch { requoteBeforeExpiry(requoted) }
    }

    private suspend fun requoteBeforeExpiry(review: PerpetualReview) {
        val expiresAt = review.expiresAtMs ?: return
        delay((expiresAt - timeProvider.currentTimeMillis() - REQUOTE_LEAD_MS).coerceAtLeast(0L))
        // A send in flight owns this quote; re-quote once it is over, if the screen is still here.
        sendingState.first { it == PerpetualSendingState.None }
        load()
    }

    private suspend fun submit() {
        if (sendingState.value != PerpetualSendingState.None || loadingState.value || loadFailure.value != null) {
            return
        }
        val loaded = reviewState.value ?: return
        sendingState.value = PerpetualSendingState.Loading
        submitFailure.value = null
        try {
            val cryptoWallet = walletUnlocker.unlockWallet(loaded.walletId, PasscodeReason.PERPS)
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
            val requoted = (e as? PerpetualConfirmException)?.requoted
            requoted?.let(::show)
            sendingState.value = PerpetualSendingState.None
            submitFailure.value = e.toConfirmError()
            if (requoted == null && e is PerpetualConfirmException && e.requote) {
                load()
            }
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
