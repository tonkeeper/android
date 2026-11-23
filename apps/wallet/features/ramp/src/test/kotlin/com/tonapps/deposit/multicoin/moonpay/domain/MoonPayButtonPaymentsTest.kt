package com.tonapps.deposit.multicoin.moonpay.domain

import com.tonapps.deposit.multicoin.moonpay.api.MoonPayAsset
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyQuote
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayMoney
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameCommand
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayPaymentData
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MoonPayButtonPaymentsTest {

    private val states = MutableStateFlow<MoonPayPaymentState>(MoonPayPaymentState.Starting)
    private val commands = mutableListOf<MoonPayFrameCommand>()

    // The frame listens for commands once it is drawn: until then a quote sent to it waits.
    private val frameListens = MutableStateFlow(true)

    // The payment as the interactor runs it: the frame's commands are taken, the states end with the first final one.
    private val interactor = mockk<MoonPayPaymentInteractor> {
        every { pay(any(), any(), any(), any(), any()) } answers {
            val sent = arg<Flow<MoonPayFrameCommand>>(4)
            channelFlow {
                val frame = launch {
                    frameListens.first { it }
                    sent.collect { commands += it }
                }
                states.transformWhile { state ->
                    emit(state)
                    !state.isFinal
                }.collect { send(it) }
                frame.cancel()
            }
        }
    }

    private val data = MoonPayPaymentData(quote = quote("first"), paymentTitle = "Google Pay", externalTransactionId = "order", theme = null)

    @Test
    fun `the button never asks to sign in, as nobody tapped it yet`() = runTest {
        buttons().start(data)

        verify { interactor.pay(data.quote, null, "order", false, any()) }
    }

    @Test
    fun `the status sheet sees the whole payment from its start, up to its end, even once it ended`() = runTest {
        val buttons = buttons()
        val button = buttons.start(data)
        states.value = MoonPayPaymentState.ButtonReady
        states.value = MoonPayPaymentState.ButtonPressed
        states.value = MoonPayPaymentState.Completed("tx")
        val seen = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { checkNotNull(buttons.attach("order")).toList() }

        assertTrue(button.pressed)
        assertEquals(
            listOf(
                MoonPayPaymentState.Starting,
                MoonPayPaymentState.ButtonReady,
                MoonPayPaymentState.ButtonPressed,
                MoonPayPaymentState.Completed("tx"),
            ),
            seen.await(),
        )
        assertNull(buttons.attach("other"))
    }

    @Test
    fun `a newer quote goes to an untapped button once, never to a tapped one`() = runTest {
        val buttons = buttons()
        val button = buttons.start(data)
        buttons.setQuote("order", quote("first"))
        buttons.setQuote("order", quote("second"))
        buttons.setQuote("order", quote("second"))
        states.value = MoonPayPaymentState.ButtonPressed
        buttons.setQuote("order", quote("third"))

        assertEquals(listOf<MoonPayFrameCommand>(MoonPayFrameCommand.SetQuote("second")), commands)
        assertEquals("second", button.quote.value.signature)
    }

    @Test
    fun `a frame that listens late gets the last quote sent before it`() = runTest {
        frameListens.value = false
        val buttons = buttons()
        val button = buttons.start(data)
        buttons.setQuote("order", quote("second"))
        buttons.setQuote("order", quote("third"))
        frameListens.value = true

        assertEquals(listOf<MoonPayFrameCommand>(MoonPayFrameCommand.SetQuote("third")), commands)
    }

    @Test
    fun `the tap keeps the quote the frame held then`() = runTest {
        val buttons = buttons()
        val button = buttons.start(data)
        buttons.setQuote("order", quote("second"))
        states.value = MoonPayPaymentState.ButtonPressed

        assertEquals("second", button.pressedWith?.signature)
    }

    @Test
    fun `an untapped button is dropped, a tapped one only by its sheet`() = runTest {
        val buttons = buttons()
        val untapped = buttons.start(data)
        buttons.discard(untapped)
        assertFalse(untapped.job.isActive)
        assertNull(buttons.attach("order"))

        val tapped = buttons.start(data.copy(externalTransactionId = "tapped"))
        states.value = MoonPayPaymentState.ButtonPressed
        buttons.discard(tapped)
        assertTrue(tapped.job.isActive)
        buttons.stop("tapped")
        assertFalse(tapped.job.isActive)
        assertNull(buttons.attach("tapped"))
    }

    @Test
    fun `a payment that throws ends with an unknown outcome, not a hanging sheet`() = runTest {
        every { interactor.pay(any(), any(), any(), any(), any()) } returns flow {
            emit(MoonPayPaymentState.ButtonPressed)
            error("boom")
        }
        val buttons = buttons()
        buttons.start(data)

        assertEquals(
            listOf(MoonPayPaymentState.ButtonPressed, MoonPayPaymentState.Unknown(transactionId = null)),
            checkNotNull(buttons.attach("order")).toList(),
        )
    }

    private fun TestScope.buttons(): MoonPayButtonPayments {
        return MoonPayButtonPayments(interactor, CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
    }

    private fun quote(signature: String): MoonPayBuyQuote {
        return MoonPayBuyQuote(
            source = MoonPayMoney(amount = "50", asset = MoonPayAsset(code = "usd")),
            destination = MoonPayMoney(amount = "0.01", asset = MoonPayAsset(code = "eth")),
            signature = signature,
        )
    }
}
