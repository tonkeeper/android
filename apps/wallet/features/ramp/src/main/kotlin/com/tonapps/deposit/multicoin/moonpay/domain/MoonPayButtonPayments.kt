package com.tonapps.deposit.multicoin.moonpay.domain

import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyQuote
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameCommand
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayPaymentData
import com.tonapps.log.L
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

// Payments started by MoonPay's buy button (Google Pay). The button is a payment frame that runs before the customer
// pays: the buying screen starts it with a quote and shows it, and the status sheet takes its outcome over after the
// tap. The payment outlives both screens, so it runs here, by its `externalTransactionId`, until one of them drops it.
class MoonPayButtonPayments(
    private val interactor: MoonPayPaymentInteractor,
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, e -> L.e(e) },
    ),
) {

    class Button internal constructor(
        val data: MoonPayPaymentData,
        // Every state from the start, so a late subscriber (the status sheet) sees the whole payment.
        val states: SharedFlow<MoonPayPaymentState>,
        private val inFrame: MutableStateFlow<MoonPayBuyQuote>,
        internal val job: Job,
    ) {

        // The quote the frame pays: the one it loaded with, then each one sent to it. A frame that subscribes late
        // still gets the last one.
        val quote: StateFlow<MoonPayBuyQuote> = inFrame.asStateFlow()

        // The quote the frame held when the customer tapped; null before the tap.
        @Volatile
        var pressedWith: MoonPayBuyQuote? = null
            internal set

        val pressed: Boolean
            get() = pressedWith != null

        // The frame loads with the first quote: only the ones after it are sent, the last one first to a late listener.
        internal val commands: Flow<MoonPayFrameCommand> = inFrame
            .map { it.signature }
            .distinctUntilChanged()
            .filter { it != data.quote.signature }
            .map(MoonPayFrameCommand::SetQuote)

        internal fun send(quote: MoonPayBuyQuote) {
            inFrame.value = quote
        }
    }

    private val buttons = ConcurrentHashMap<String, Button>()

    fun start(data: MoonPayPaymentData): Button {
        val states = MutableSharedFlow<MoonPayPaymentState>(replay = Int.MAX_VALUE)
        val inFrame = MutableStateFlow(data.quote)
        lateinit var button: Button
        val job = scope.launch(start = CoroutineStart.LAZY) {
            interactor.pay(data.quote, data.theme, data.externalTransactionId, interactive = false, commands = button.commands)
                .catch { e ->
                    if (e is CancellationException) {
                        throw e
                    }
                    L.e(e)
                    emit(MoonPayPaymentState.Unknown(transactionId = null))
                }
                .collect { state ->
                    if (state is MoonPayPaymentState.ButtonPressed && button.pressedWith == null) {
                        button.pressedWith = button.quote.value
                    }
                    states.emit(state)
                }
        }
        button = Button(data, states.asSharedFlow(), inFrame, job)
        buttons[data.externalTransactionId] = button
        job.start()
        return button
    }

    // A newer quote for a button nobody tapped yet, so it does not run out while the customer looks at it.
    fun setQuote(externalTransactionId: String, quote: MoonPayBuyQuote) {
        val button = buttons[externalTransactionId] ?: return
        if (!button.pressed && button.job.isActive) {
            button.send(quote)
        }
    }

    // The payment's states up to its end, for the status sheet; null for a payment that is not a button's. The
    // payment stays here after its end, so a sheet opened late still gets it.
    fun attach(externalTransactionId: String): Flow<MoonPayPaymentState>? {
        val button = buttons[externalTransactionId] ?: return null
        return button.states.transformWhile { state ->
            emit(state)
            !state.isFinal
        }
    }

    // Drops a button the screen no longer shows; a tapped one is the status sheet's until it stops it.
    fun discard(button: Button) {
        if (!button.pressed) {
            button.job.cancel()
            buttons.remove(button.data.externalTransactionId, button)
        }
    }

    // The status sheet left: the payment stops with it, as one it started would.
    fun stop(externalTransactionId: String) {
        buttons.remove(externalTransactionId)?.job?.cancel()
    }
}

internal val MoonPayPaymentState.isFinal: Boolean
    get() = this is MoonPayPaymentState.Completed ||
        this is MoonPayPaymentState.Unknown ||
        this is MoonPayPaymentState.Failed
