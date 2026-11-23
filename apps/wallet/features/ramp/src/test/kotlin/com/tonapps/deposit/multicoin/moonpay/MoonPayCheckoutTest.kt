package com.tonapps.deposit.multicoin.moonpay

import kotlin.time.Duration.Companion.minutes
import com.tonapps.deposit.multicoin.moonpay.MoonPayCheckoutError.Source
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayAsset
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyQuote
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayEnvironment
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayMoney
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPaymentMethodReference
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayTransactionDetails
import io.mockk.coEvery
import io.mockk.mockk
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import com.tonapps.deposit.multicoin.moonpay.crypto.MoonPayCredentials
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrame
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameCompletion
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameError
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameEvent
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameException
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrames
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayTheme
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayTransaction
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class MoonPayCheckoutTest {

    private val connection = MoonPayConnection(
        environment = MoonPayEnvironment.Production,
        credentials = MoonPayCredentials("access", "client", "2030-01-01T00:00:00Z"),
    )

    private val frames = MoonPayFrames()

    // Each call takes the next answer; the first is the snapshot taken when the widget opens.
    private val listed = ArrayDeque<Result<List<MoonPayTransactionDetails>>>()
    private val sinceCalls = mutableListOf<Long>()

    private val transactions = mockk<MoonPayTransactionRepository> {
        coEvery { transactionsSince(any(), any()) } answers {
            sinceCalls += firstArg<Long>()
            listed.removeFirstOrNull() ?: Result.success(emptyList())
        }
    }

    private val foreground = MutableStateFlow(true)

    private val checkout = MoonPayCheckout(frames, transactions, foreground) { OPENED_AT }

    private val events = mutableListOf<MoonPayCheckoutEvent>()
    private val opened = mutableListOf<Long>()

    @Before
    fun attachHost() {
        frames.attachHost(Any())
    }

    @Test
    fun `a card is paid in a headless buy frame that starts on ready`() = runTest {
        val job = run(quote(method = "card"))

        val frame = paymentFrame() as MoonPayFrame.Buy
        assertEquals("client", frame.clientToken)
        assertEquals("signature", frame.signature)
        payment().onEvent(MoonPayFrameEvent.Ready)
        payment().onEvent(complete("completed"))

        assertEquals(
            listOf(
                MoonPayCheckoutEvent.Started(null),
                MoonPayCheckoutEvent.Finished(transaction("completed"), sessionUpgraded = false),
            ),
            events,
        )
        assertTrue(job.isCompleted)
        assertNoFrames()
    }

    @Test
    fun `a transaction named after the start is sent once`() = runTest {
        run(quote(method = "card"))
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Ready)
        payment().onEvent(MoonPayFrameEvent.TransactionCreated(transaction("pending")))
        payment().onEvent(MoonPayFrameEvent.TransactionCreated(transaction("pending")))

        assertEquals(
            listOf(MoonPayCheckoutEvent.Started(null), MoonPayCheckoutEvent.Started(transaction("pending"))),
            events,
        )
    }

    @Test
    fun `other methods are paid in the widget, which starts with its transaction`() = runTest {
        run(quote(method = "revolut_pay"), theme = MoonPayTheme.Dark)

        assertEquals(MoonPayTheme.Dark, (paymentFrame() as MoonPayFrame.Widget).theme)
        payment().onEvent(MoonPayFrameEvent.Ready)
        assertTrue(events.isEmpty())
        payment().onEvent(MoonPayFrameEvent.TransactionCreated(transaction("pending")))
        payment().onEvent(MoonPayFrameEvent.TransactionCreated(transaction("pending")))

        assertEquals(listOf(MoonPayCheckoutEvent.Started(transaction("pending"))), events)
    }

    @Test
    fun `the order's external transaction id goes to the frame`() = runTest {
        run(quote())
        assertEquals(ORDER_ID, (paymentFrame() as MoonPayFrame.Buy).externalTransactionId)
    }

    @Test
    fun `an upgraded session is reported with the transaction`() = runTest {
        run(quote())
        paymentFrame()

        payment().onEvent(
            MoonPayFrameEvent.Complete(MoonPayFrameCompletion.Transaction(transaction("pending"), sessionUpgraded = true)),
        )

        assertEquals(MoonPayCheckoutEvent.Finished(transaction("pending"), sessionUpgraded = true), events.single())
    }

    @Test
    fun `a payment frame error, an expired quote included, fails with its code`() = runTest {
        for (code in listOf("invalidQuote", "quoteExpired")) {
            events.clear()
            run(quote())
            paymentFrame()
            val error = MoonPayFrameError(code, "cannot use")

            payment().onEvent(MoonPayFrameEvent.Error(error))

            assertFailed(MoonPayCheckoutError.Remote(Source.Payment, error))
            assertNoFrames()
        }
    }

    @Test
    fun `MoonPay's cancellation is a cancellation even after the start`() = runTest {
        run(quote())
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Ready)

        payment().onEvent(MoonPayFrameEvent.Cancelled(null))

        assertEquals(MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.Cancelled, null), events.last())
    }

    @Test
    fun `a frame closed or lost before the start is a safe failure`() = runTest {
        run(quote())
        paymentFrame()
        payment().onClose()
        assertFailed(MoonPayCheckoutError.Cancelled)
        assertTrue(sinceCalls.isEmpty())

        events.clear()
        run(quote())
        paymentFrame()
        val failure = MoonPayFrameException.HostGone()
        payment().onFailure(failure)
        assertFailed(MoonPayCheckoutError.FrameFailed(failure))
    }

    @Test
    fun `a frame closed or lost after the start leaves the outcome unknown`() = runTest {
        run(quote(method = "revolut_pay"))
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.TransactionCreated(transaction("pending")))
        payment().onClose()
        assertEquals(MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.NoOutcome(null), "tx-1"), events.last())

        events.clear()
        run(quote())
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Ready)
        val failure = MoonPayFrameException.HostGone()
        payment().onFailure(failure)
        assertEquals(MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.NoOutcome(failure), null), events.last())
        assertNoFrames()
    }

    @Test
    fun `a buy frame that never starts gives no response`() = runTest {
        run(quote())
        paymentFrame()

        advanceTimeBy(61.seconds)

        assertFailed(MoonPayCheckoutError.NoResponse)
        assertNoFrames()
    }

    @Test
    fun `a started purchase that never ends has no outcome`() = runTest {
        run(quote())
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Ready)

        advanceTimeBy(179.seconds)
        assertEquals(listOf<MoonPayCheckoutEvent>(MoonPayCheckoutEvent.Started(null)), events)
        advanceTimeBy(2.seconds)

        assertEquals(MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.NoOutcome(null), null), events.last())
        assertNoFrames()
    }

    @Test
    fun `a challenge that shows up stops the deadline`() = runTest {
        run(quote())
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Challenge(CHALLENGE_URL))
        challenge().onEvent(MoonPayFrameEvent.Ready)

        advanceTimeBy(600.seconds)

        assertEquals(listOf<MoonPayCheckoutEvent>(MoonPayCheckoutEvent.Started(null)), events)
    }

    @Test
    fun `the widget has no deadline`() = runTest {
        run(quote(method = "revolut_pay"))
        paymentFrame()

        advanceTimeBy(600.seconds)

        assertTrue(events.isEmpty())
    }

    @Test
    fun `a challenge opens over the payment and can finish the purchase`() = runTest {
        run(quote(), theme = MoonPayTheme.Light)
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Ready)

        payment().onEvent(MoonPayFrameEvent.Challenge(CHALLENGE_URL))
        val frame = challenge().frame as MoonPayFrame.Challenge
        assertEquals(CHALLENGE_URL, frame.url)
        assertEquals(MoonPayTheme.Light, frame.theme)
        challenge().onEvent(complete("completed"))

        assertEquals(MoonPayCheckoutEvent.Finished(transaction("completed"), sessionUpgraded = false), events.last())
        assertNoFrames()
    }

    @Test
    fun `a challenge ends the attempt with its own result`() = runTest {
        val error = MoonPayFrameError("customer_status_pending", "pending")
        val failure = MoonPayFrameException.LoadFailed("offline")
        val endings = listOf<Pair<(MoonPayFrames.Entry) -> Unit, MoonPayCheckoutError>>(
            { entry: MoonPayFrames.Entry -> entry.onEvent(MoonPayFrameEvent.Error(error)) } to
                MoonPayCheckoutError.Remote(Source.Challenge, error),
            { entry: MoonPayFrames.Entry -> entry.onEvent(MoonPayFrameEvent.Cancelled(null)) } to
                MoonPayCheckoutError.Cancelled,
            { entry: MoonPayFrames.Entry ->
                entry.onEvent(MoonPayFrameEvent.Complete(MoonPayFrameCompletion.Status("pending")))
            } to MoonPayCheckoutError.ChallengeCompleted(MoonPayFrameCompletion.Status("pending")),
            { entry: MoonPayFrames.Entry -> entry.onClose() } to MoonPayCheckoutError.ChallengeClosed,
            { entry: MoonPayFrames.Entry -> entry.onFailure(failure) } to MoonPayCheckoutError.NoOutcome(failure),
        )
        for ((end, expected) in endings) {
            events.clear()
            run(quote())
            paymentFrame()
            payment().onEvent(MoonPayFrameEvent.Challenge(CHALLENGE_URL))

            end(challenge())

            assertEquals(MoonPayCheckoutEvent.Failed(expected, null), events.last())
            assertNoFrames()
        }
    }

    @Test
    fun `a payment frame lost under an open challenge leaves the challenge to finish`() = runTest {
        run(quote())
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Challenge(CHALLENGE_URL))
        val challenge = challenge()

        payment().onFailure(MoonPayFrameException.LoadFailed("renderer gone"))
        assertEquals(listOf<MoonPayFrame>(challenge.frame), frames.state.value.entries.map { it.frame })
        challenge.onEvent(complete("completed"))

        assertEquals(MoonPayCheckoutEvent.Finished(transaction("completed"), sessionUpgraded = false), events.last())
        assertNoFrames()
    }

    @Test
    fun `a cancelled challenge names the transaction it leaves`() = runTest {
        run(quote())
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Challenge(CHALLENGE_URL))

        challenge().onEvent(MoonPayFrameEvent.Cancelled(buildJsonObject { put("transactionId", "tx-9") }))

        assertEquals(MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.Cancelled, "tx-9"), events.last())
    }

    @Test
    fun `a failure carries the last transaction MoonPay named`() = runTest {
        run(quote(method = "revolut_pay"))
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Challenge(CHALLENGE_URL))
        payment().onEvent(MoonPayFrameEvent.TransactionCreated(transaction("pending")))

        challenge().onClose()

        assertEquals(
            listOf(
                MoonPayCheckoutEvent.Started(null),
                MoonPayCheckoutEvent.Started(transaction("pending")),
                MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.ChallengeClosed, "tx-1"),
            ),
            events,
        )
    }

    @Test
    fun `the deadline runs until the challenge frame shows up`() = runTest {
        run(quote())
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Challenge(CHALLENGE_URL))
        challenge()

        advanceTimeBy(181.seconds)

        assertEquals(MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.NoOutcome(null), null), events.last())
        assertNoFrames()
    }

    @Test
    fun `a payment frame error under an open challenge ends the attempt`() = runTest {
        run(quote())
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Challenge(CHALLENGE_URL))
        challenge()
        val error = MoonPayFrameError("generic", "boom")

        payment().onEvent(MoonPayFrameEvent.Error(error))

        assertEquals(MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.Remote(Source.Payment, error), null), events.last())
        assertNoFrames()
    }

    @Test
    fun `a completion that is not a transaction is ignored by the payment frame`() = runTest {
        run(quote(method = "revolut_pay"))
        paymentFrame()

        payment().onEvent(MoonPayFrameEvent.Complete(MoonPayFrameCompletion.Status("ok")))

        assertTrue(events.isEmpty())
    }

    @Test
    fun `a second challenge while one is open is ignored`() = runTest {
        run(quote())
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Challenge(CHALLENGE_URL))
        challenge()

        payment().onEvent(MoonPayFrameEvent.Challenge("$CHALLENGE_URL/2"))

        assertEquals(1, frames.state.value.entries.count { it.frame is MoonPayFrame.Challenge })
    }

    @Test
    fun `stopping the collection closes every frame`() = runTest {
        val job = run(quote())
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Challenge(CHALLENGE_URL))
        challenge()

        job.cancel()

        assertNoFrames()
    }

    @Test
    fun `a widget closed with a new transaction is followed by it`() = runTest {
        listed += Result.success(listOf(listedTransaction("old", OPENED_AT - 60_000)))
        listed += Result.success(listOf(listedTransaction("old", OPENED_AT - 60_000), listedTransaction("new", OPENED_AT + 5_000)))
        run(quote(method = "paypal"))
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Ready)

        payment().onClose()

        assertEquals(
            listOf(
                MoonPayCheckoutEvent.Checking,
                MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.NoOutcome(null), "new"),
            ),
            events,
        )
    }

    @Test
    fun `the lookup asks again while MoonPay does not list the transaction yet`() = runTest {
        listed += Result.success(emptyList())
        listed += Result.success(emptyList())
        listed += Result.success(listOf(listedTransaction("new", OPENED_AT + 5_000)))
        run(quote(method = "paypal"))
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Ready)

        payment().onClose()
        assertEquals(listOf<MoonPayCheckoutEvent>(MoonPayCheckoutEvent.Checking), events)
        advanceTimeBy(2.seconds + 1.milliseconds)

        assertEquals(MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.NoOutcome(null), "new"), events.last())
    }

    @Test
    fun `a widget closed with no new transaction is cancelled after three answers`() = runTest {
        listed += Result.success(listOf(listedTransaction("old", OPENED_AT - 60_000)))
        repeat(3) { listed += Result.success(listOf(listedTransaction("old", OPENED_AT - 60_000))) }
        run(quote(method = "paypal"))
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Ready)

        payment().onClose()
        advanceTimeBy(4.seconds + 1.milliseconds)

        assertEquals(
            listOf(MoonPayCheckoutEvent.Checking, MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.Cancelled, null)),
            events,
        )
        assertEquals(4, sinceCalls.size)
        assertTrue(sinceCalls.all { it == OPENED_AT - 120_000 })
    }

    @Test
    fun `a lookup whose last try fails leaves the outcome unknown`() = runTest {
        val failure = IllegalStateException("offline")
        listed += Result.success(emptyList())
        listed += Result.success(emptyList())
        listed += Result.success(emptyList())
        listed += Result.failure(failure)
        run(quote(method = "paypal"))
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Ready)

        payment().onClose()
        advanceTimeBy(4.seconds + 1.milliseconds)

        assertEquals(MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.NoOutcome(failure), null), events.last())
    }

    @Test
    fun `without the snapshot any transaction since the widget opened counts`() = runTest {
        listed += Result.failure(IllegalStateException("offline"))
        listed += Result.success(listOf(listedTransaction("new", OPENED_AT + 5_000)))
        run(quote(method = "paypal"))
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Ready)

        payment().onClose()

        assertEquals(MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.NoOutcome(null), "new"), events.last())
    }

    @Test
    fun `the widget opens on MoonPay's clock`() = runTest {
        run(quote(method = "paypal").copy(serverClockOffsetMs = 30_000))
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Ready)

        assertEquals(listOf(OPENED_AT + 30_000), opened)
        assertEquals(OPENED_AT + 30_000 - 120_000, sinceCalls.single())
    }

    @Test
    fun `without MoonPay's clock an attempt of the last minutes stays in the snapshot`() = runTest {
        listed += Result.success(listOf(listedTransaction("earlier", OPENED_AT - 60_000)))
        repeat(3) { listed += Result.success(listOf(listedTransaction("earlier", OPENED_AT - 60_000))) }
        run(quote(method = "paypal").copy(serverClockOffsetMs = null))
        paymentFrame()

        payment().onClose()
        advanceTimeBy(4.seconds + 1.milliseconds)

        assertEquals(MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.Cancelled, null), events.last())
    }

    @Test
    fun `without MoonPay's clock the widget counts as opened earlier`() = runTest {
        listed += Result.success(emptyList())
        listed += Result.success(listOf(listedTransaction("new", OPENED_AT - 60_000)))
        run(quote(method = "paypal").copy(serverClockOffsetMs = null))
        paymentFrame()

        payment().onClose()

        assertEquals(listOf(OPENED_AT - 120_000), opened)
        assertEquals(MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.NoOutcome(null), "new"), events.last())
    }

    @Test
    fun `a transaction made before the widget opened is never taken for its own`() = runTest {
        listed += Result.failure(IllegalStateException("offline"))
        repeat(3) { listed += Result.success(listOf(listedTransaction("earlier", OPENED_AT - 60_000))) }
        run(quote(method = "paypal"))
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Ready)

        payment().onClose()
        advanceTimeBy(4.seconds + 1.milliseconds)

        assertEquals(MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.Cancelled, null), events.last())
    }

    @Test
    fun `a widget that never answered is looked up too`() = runTest {
        listed += Result.success(emptyList())
        listed += Result.success(listOf(listedTransaction("new", OPENED_AT + 5_000)))
        run(quote(method = "paypal"))
        paymentFrame()

        payment().onClose()

        assertEquals(MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.NoOutcome(null), "new"), events.last())
    }

    @Test
    fun `an empty last answer after a failed try is not taken for no payment`() = runTest {
        listed += Result.success(emptyList())
        listed += Result.failure(IllegalStateException("offline"))
        listed += Result.success(emptyList())
        listed += Result.success(emptyList())
        run(quote(method = "paypal"))
        paymentFrame()

        payment().onClose()
        advanceTimeBy(4.seconds + 1.milliseconds)

        assertTrue((events.last() as MoonPayCheckoutEvent.Failed).error is MoonPayCheckoutError.NoOutcome)
    }

    @Test
    fun `a widget that never answered and cannot be looked up leaves the outcome unknown`() = runTest {
        repeat(4) { listed += Result.failure(IllegalStateException("offline")) }
        run(quote(method = "paypal"))
        paymentFrame()
        payment().onClose()
        advanceTimeBy(4.seconds + 1.milliseconds)
        assertTrue((events.last() as MoonPayCheckoutEvent.Failed).error is MoonPayCheckoutError.NoOutcome)

        events.clear()
        repeat(4) { listed += Result.failure(IllegalStateException("offline")) }
        run(quote(method = "paypal"))
        paymentFrame()
        val failure = MoonPayFrameException.HostGone()
        payment().onFailure(failure)
        advanceTimeBy(4.seconds + 1.milliseconds)
        assertEquals(MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.NoOutcome(failure), null), events.last())
    }

    @Test
    fun `a widget opens once and a start keeps its phase`() = runTest {
        run(quote(method = "paypal"))
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.TransactionCreated(transaction("pending")))
        payment().onEvent(MoonPayFrameEvent.Ready)

        assertEquals(listOf(OPENED_AT), opened)
        assertEquals(listOf<MoonPayCheckoutEvent>(MoonPayCheckoutEvent.Started(transaction("pending"))), events)
    }

    @Test
    fun `the lookup waits until the app is back on screen`() = runTest {
        foreground.value = false
        listed += Result.success(emptyList())
        listed += Result.success(listOf(listedTransaction("new", OPENED_AT + 5_000)))
        run(quote(method = "paypal"))
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Ready)

        payment().onClose()
        advanceTimeBy(10.seconds)
        assertEquals(listOf<MoonPayCheckoutEvent>(MoonPayCheckoutEvent.Checking), events)

        foreground.value = true
        assertEquals(MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.NoOutcome(null), "new"), events.last())
    }

    @Test
    fun `a failed widget is looked up too`() = runTest {
        listed += Result.success(emptyList())
        listed += Result.success(listOf(listedTransaction("new", OPENED_AT + 5_000)))
        run(quote(method = "paypal"))
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Ready)
        val failure = MoonPayFrameException.HostGone()
        payment().onFailure(failure)

        assertEquals(
            listOf(
                MoonPayCheckoutEvent.Checking,
                MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.NoOutcome(failure), "new"),
            ),
            events,
        )
    }

    @Test
    fun `a failed widget with no transaction keeps its failure`() = runTest {
        run(quote(method = "paypal"))
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Ready)
        val failure = MoonPayFrameException.HostGone()
        payment().onFailure(failure)
        advanceTimeBy(4.seconds + 1.milliseconds)

        assertEquals(MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.FrameFailed(failure), null), events.last())
    }

    @Test
    fun `Google Pay is paid from MoonPay's button, which waits for a tap`() = runTest {
        run(quote(method = "google_pay"), theme = MoonPayTheme.Light)
        val frame = paymentFrame() as MoonPayFrame.BuyButton
        payment().onEvent(MoonPayFrameEvent.Ready)
        advanceTimeBy(61.seconds)

        assertEquals("signature", frame.signature)
        assertEquals(ORDER_ID, frame.externalTransactionId)
        assertTrue(frame.googlePayButtonSettings.contains("\"style\":\"black\""))
        assertEquals(listOf<MoonPayCheckoutEvent>(MoonPayCheckoutEvent.ButtonReady), events)
        assertTrue(opened.isEmpty())
    }

    @Test
    fun `a tap opens the button as a widget, so a frame lost after it is looked up`() = runTest {
        listed += Result.success(emptyList())
        listed += Result.success(listOf(listedTransaction("new", OPENED_AT + 5_000)))
        run(quote(method = "google_pay"))
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Ready)
        payment().onEvent(MoonPayFrameEvent.ButtonPressed)
        payment().onEvent(MoonPayFrameEvent.ButtonPressed)
        val failure = MoonPayFrameException.HostGone()
        payment().onFailure(failure)

        assertEquals(listOf(OPENED_AT), opened)
        assertEquals(
            listOf(
                MoonPayCheckoutEvent.ButtonReady,
                MoonPayCheckoutEvent.ButtonPressed,
                MoonPayCheckoutEvent.Checking,
                MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.NoOutcome(failure), "new"),
            ),
            events,
        )
    }

    @Test
    fun `a button lost before a tap is a safe failure, not looked up`() = runTest {
        run(quote(method = "google_pay"))
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.Ready)
        val failure = MoonPayFrameException.HostGone()
        payment().onFailure(failure)

        assertEquals(
            listOf(MoonPayCheckoutEvent.ButtonReady, MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.FrameFailed(failure), null)),
            events,
        )
    }

    @Test
    fun `the wallet's sheet closed after a tap is a cancellation, not looked up`() = runTest {
        run(quote(method = "google_pay"))
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.ButtonPressed)
        payment().onEvent(MoonPayFrameEvent.Cancelled(null))

        assertEquals(
            listOf(MoonPayCheckoutEvent.ButtonPressed, MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.Cancelled, null)),
            events,
        )
    }

    @Test
    fun `a wallet payment challenged before its transaction resumes on the button, and resolved sends the customer back to it`() = runTest {
        run(quote(method = "google_pay"))
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.ButtonPressed)
        payment().onEvent(MoonPayFrameEvent.Challenge(CHALLENGE_URL))
        val challenge = challenge()
        challenge.onEvent(MoonPayFrameEvent.Resolved)

        assertEquals(MoonPayFrame.Challenge(CHALLENGE_URL, "client", null, resume = true), challenge.frame)
        assertEquals(MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.Cancelled, null), events.last())
    }

    @Test
    fun `a button payment MoonPay named and then went silent about ends with no outcome, as the buy frame's`() = runTest {
        run(quote(method = "google_pay"))
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.ButtonPressed)
        payment().onEvent(MoonPayFrameEvent.TransactionCreated(transaction("pending")))
        advanceTimeBy(3.minutes + 1.milliseconds)

        assertEquals(MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.NoOutcome(null), "tx-1"), events.last())
    }

    @Test
    fun `a payment the button reports before its tap counts as the tap`() = runTest {
        run(quote(method = "google_pay"))
        paymentFrame()
        payment().onEvent(MoonPayFrameEvent.TransactionCreated(transaction("pending")))

        assertEquals(listOf(OPENED_AT), opened)
        assertEquals(
            listOf(MoonPayCheckoutEvent.ButtonPressed, MoonPayCheckoutEvent.Started(transaction("pending"))),
            events,
        )
    }

    private fun listedTransaction(id: String, createdAt: Long): MoonPayTransactionDetails {
        return MoonPayTransactionDetails(id = id, status = "pending", createdAt = Instant.ofEpochMilli(createdAt).toString())
    }

    private fun TestScope.run(quote: MoonPayBuyQuote, theme: MoonPayTheme? = null): Job {
        return backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            checkout.run(connection, quote, theme, ORDER_ID, emptyFlow()).collect { event ->
                if (event is MoonPayCheckoutEvent.WidgetOpened) {
                    opened += event.openedAt
                } else {
                    events += event
                }
            }
        }
    }

    private suspend fun paymentFrame(): MoonPayFrame {
        val state = frames.state.first { state -> state.entries.any { it.frame !is MoonPayFrame.Challenge } }
        return state.entries.first { it.frame !is MoonPayFrame.Challenge }.frame
    }

    private fun payment(): MoonPayFrames.Entry {
        return frames.state.value.entries.first { it.frame !is MoonPayFrame.Challenge }
    }

    private suspend fun challenge(): MoonPayFrames.Entry {
        val state = frames.state.first { state -> state.entries.any { it.frame is MoonPayFrame.Challenge } }
        return state.entries.first { it.frame is MoonPayFrame.Challenge }
    }

    private fun assertFailed(error: MoonPayCheckoutError) {
        assertEquals(listOf<MoonPayCheckoutEvent>(MoonPayCheckoutEvent.Failed(error, null)), events)
    }

    private fun assertNoFrames() {
        assertTrue(frames.state.value.entries.isEmpty())
    }

    private fun transaction(status: String): MoonPayTransaction = MoonPayTransaction("tx-1", status)

    private fun complete(status: String): MoonPayFrameEvent {
        return MoonPayFrameEvent.Complete(MoonPayFrameCompletion.Transaction(transaction(status)))
    }

    private fun quote(method: String = "card"): MoonPayBuyQuote {
        return MoonPayBuyQuote(
            source = MoonPayMoney(amount = "100", asset = MoonPayAsset(code = "usd")),
            destination = MoonPayMoney(amount = "20", asset = MoonPayAsset(code = "ton")),
            paymentMethod = MoonPayPaymentMethodReference(type = method),
            executable = true,
            signature = "signature",
            serverClockOffsetMs = 0,
        )
    }

    private companion object {
        const val ORDER_ID = "order-1"
        const val OPENED_AT = 1_800_000_000_000L
        const val CHALLENGE_URL = "https://platform.moonpay.com/challenge/1"
    }
}
