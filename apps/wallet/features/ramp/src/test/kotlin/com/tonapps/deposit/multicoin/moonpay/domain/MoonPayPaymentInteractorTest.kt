package com.tonapps.deposit.multicoin.moonpay.domain

import com.tonapps.deposit.multicoin.moonpay.MoonPayCheckout
import com.tonapps.deposit.multicoin.moonpay.MoonPayCheckoutError
import com.tonapps.deposit.multicoin.moonpay.MoonPayCheckoutEvent
import com.tonapps.deposit.multicoin.moonpay.MoonPayConnection
import com.tonapps.deposit.multicoin.moonpay.MoonPayConnectionException
import com.tonapps.deposit.multicoin.moonpay.MoonPayConnectionRepository
import com.tonapps.deposit.multicoin.moonpay.MoonPayTransactionRepository
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayApiException
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayAsset
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayBuyQuote
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayEnvironment
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayMoney
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayPaymentMethodReference
import com.tonapps.deposit.multicoin.moonpay.api.MoonPayTransactionDetails
import com.tonapps.deposit.multicoin.moonpay.crypto.MoonPayCredentials
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayConnectionStatus
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameError
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrameException
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayTransaction
import com.tonapps.test.silenceLog
import io.mockk.coEvery
import io.mockk.coVerify
import com.tonapps.deposit.multicoin.moonpay.MoonPayWidgetLookup
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.Base64
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class MoonPayPaymentInteractorTest {

    private val connection = MoonPayConnection(
        environment = MoonPayEnvironment.Production,
        credentials = MoonPayCredentials("access", "client", "2030-01-01T00:00:00Z"),
    )

    private val connections = mockk<MoonPayConnectionRepository>(relaxUnitFun = true) {
        coEvery { ensureConnected(any(), true) } returns Result.success(connection)
    }

    private val checkout = mockk<MoonPayCheckout>()

    private val polled = ArrayDeque<Result<MoonPayTransactionDetails>>()

    private val transactions = mockk<MoonPayTransactionRepository> {
        coEvery { transaction("tx-1", any()) } answers {
            polled.removeFirstOrNull() ?: Result.failure(IOException("offline"))
        }
    }

    private var clock: () -> Long = { 0L }

    private val foreground = MutableStateFlow(true)

    private val interactor = MoonPayPaymentInteractor(connections, checkout, transactions, foreground) { clock() }

    @Before
    fun setUp() {
        silenceLog()
    }

    @Test
    fun `a payment goes from start to completion`() = runPayment {
        checkout(
            MoonPayCheckoutEvent.Started(transaction("pending")),
            MoonPayCheckoutEvent.Finished(transaction("completed"), sessionUpgraded = false),
        )

        assertEquals(
            listOf(
                MoonPayPaymentState.Starting,
                MoonPayPaymentState.Started("tx-1"),
                MoonPayPaymentState.Completed("tx-1"),
            ),
            pay(),
        )
    }

    @Test
    fun `an unusable quote is rejected before connecting`() = runPayment {
        assertEquals(
            listOf(MoonPayPaymentState.Starting, MoonPayPaymentState.Failed(MoonPayPaymentFailure.QuoteExpired, null)),
            pay(quote(expired = true)),
        )
        val quotes = listOf(
            quote(executable = false),
            quote(signature = jwt("""{"signature":"s"}""")),
            quote(signature = "opaque"),
        )
        for (quote in quotes) {
            assertEquals(
                listOf(MoonPayPaymentState.Starting, MoonPayPaymentState.Failed(MoonPayPaymentFailure.QuoteRejected, null)),
                pay(quote),
            )
        }
        coVerify(exactly = 0) { connections.ensureConnected(any(), any()) }
    }

    @Test
    fun `a failed connection is mapped to what the customer can do`() = runPayment {
        coEvery { connections.ensureConnected(any(), true) } returns Result.failure(MoonPayConnectionException.Cancelled())

        assertEquals(MoonPayPaymentState.Failed(MoonPayPaymentFailure.Cancelled, null), pay().last())
        verify(exactly = 0) { checkout.run(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `any failure after MoonPay named the transaction is settled by its status`() = runPayment {
        val failures = listOf(
            MoonPayCheckoutError.Remote(MoonPayCheckoutError.Source.Payment, MoonPayFrameError("apiError", "boom")),
            MoonPayCheckoutError.Cancelled,
            MoonPayCheckoutError.NoOutcome(null),
        )
        for (failure in failures) {
            checkout(
                MoonPayCheckoutEvent.Started(transaction("pending")),
                MoonPayCheckoutEvent.Failed(failure, null),
            )
            polled += details("failed", listOf(MoonPayTransactionDetails.Stage(status = "failed", failureReason = "rejected_card")))

            assertEquals(
                listOf(
                    MoonPayPaymentState.Starting,
                    MoonPayPaymentState.Started("tx-1"),
                    MoonPayPaymentState.Verifying("tx-1"),
                    MoonPayPaymentState.Failed(MoonPayPaymentFailure.Declined, "tx-1"),
                ),
                pay(),
            )
        }
    }

    @Test
    fun `MoonPay's own cancel with a transaction is followed until it settles`() = runPayment {
        checkout(
            MoonPayCheckoutEvent.Started(null),
            MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.Cancelled, "tx-1"),
        )
        polled += details("pending")
        polled += details("completed")

        assertEquals(MoonPayPaymentState.Completed("tx-1"), pay().last())
    }

    @Test
    fun `a 3DS page closed with a transaction MoonPay settled shows it after one look`() = runPayment {
        checkout(
            MoonPayCheckoutEvent.Started(null),
            MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.ChallengeClosed, "tx-1"),
        )
        polled += details("completed")
        polled += details("failed")

        assertEquals(MoonPayPaymentState.Completed("tx-1"), pay().last())
        assertEquals(1, polled.size)
    }

    @Test
    fun `a 3DS page closed with a transaction MoonPay still has pending is unknown after one look`() = runPayment {
        checkout(
            MoonPayCheckoutEvent.Started(null),
            MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.ChallengeClosed, "tx-1"),
        )
        polled += details("pending")
        polled += details("completed")

        assertEquals(
            listOf(
                MoonPayPaymentState.Starting,
                MoonPayPaymentState.Started(null),
                MoonPayPaymentState.Verifying("tx-1"),
                MoonPayPaymentState.Unknown("tx-1"),
            ),
            pay(),
        )
        assertEquals(1, polled.size)
    }

    @Test
    fun `a 3DS page closed with a transaction MoonPay does not answer for is unknown`() = runPayment {
        checkout(
            MoonPayCheckoutEvent.Started(null),
            MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.ChallengeClosed, "tx-1"),
        )

        assertEquals(MoonPayPaymentState.Unknown("tx-1"), pay().last())
    }

    @Test
    fun `a restored check of a transaction asks MoonPay again`() = runPayment {
        polled += details("completed")

        assertEquals(
            listOf(MoonPayPaymentState.Verifying("tx-1"), MoonPayPaymentState.Completed("tx-1")),
            interactor.verify("tx-1", theme = null).toList(),
        )
    }

    @Test
    fun `a restored widget payment is found by its time and followed`() = runPayment {
        coEvery { checkout.findWidgetTransaction(1_000L, null, null) } returns MoonPayWidgetLookup.Found("tx-1")
        polled += details("completed")

        assertEquals(
            listOf(MoonPayPaymentState.Checking, MoonPayPaymentState.Verifying("tx-1"), MoonPayPaymentState.Completed("tx-1")),
            interactor.lookUpWidget(1_000L, theme = null).toList(),
        )
    }

    @Test
    fun `a restored widget whose payment is not found or cannot be checked is unknown`() = runPayment {
        coEvery { checkout.findWidgetTransaction(1_000L, null, null) } returns MoonPayWidgetLookup.None
        assertEquals(MoonPayPaymentState.Unknown(null), interactor.lookUpWidget(1_000L, theme = null).toList().last())

        coEvery { checkout.findWidgetTransaction(1_000L, null, null) } returns MoonPayWidgetLookup.Failed(null)
        assertEquals(MoonPayPaymentState.Unknown(null), interactor.lookUpWidget(1_000L, theme = null).toList().last())
    }

    @Test
    fun `a widget closed without its transaction is looked up before the outcome`() = runPayment {
        checkout(
            MoonPayCheckoutEvent.Checking,
            MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.Cancelled, null),
        )

        assertEquals(
            listOf(
                MoonPayPaymentState.Starting,
                MoonPayPaymentState.Checking,
                MoonPayPaymentState.Failed(MoonPayPaymentFailure.Cancelled, transactionId = null),
            ),
            pay(),
        )
    }

    @Test
    fun `a transaction named only by the failure is followed`() = runPayment {
        checkout(
            MoonPayCheckoutEvent.Started(null),
            MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.Cancelled, "tx-1"),
        )
        polled += details("completed")

        assertEquals(MoonPayPaymentState.Completed("tx-1"), pay().last())
    }

    @Test
    fun `polling stops when MoonPay asks to sign in`() = runPayment {
        checkout(MoonPayCheckoutEvent.Finished(transaction("pending"), sessionUpgraded = false))
        polled += Result.failure(MoonPayConnectionException.Rejected(MoonPayConnectionStatus.ConnectionRequired))

        assertEquals(MoonPayPaymentState.Unknown("tx-1"), pay().last())
        assertEquals(3_000L, testScheduler.currentTime)
    }

    @Test
    fun `a started card purchase cut off from outside has an unknown outcome`() = runPayment {
        every { checkout.run(connection, any(), any(), "order-1", any()) } returns flow {
            emit(MoonPayCheckoutEvent.Started(null))
            throw CancellationException("host left")
        }

        assertEquals(
            listOf(
                MoonPayPaymentState.Starting,
                MoonPayPaymentState.Started(null),
                MoonPayPaymentState.Unknown(null),
            ),
            pay(),
        )
    }

    @Test
    fun `a checkout cancelled from outside after its transaction is followed`() = runPayment {
        every { checkout.run(connection, any(), any(), "order-1", any()) } returns flow {
            emit(MoonPayCheckoutEvent.Started(transaction("pending")))
            throw CancellationException("host left")
        }
        polled += details("completed")

        assertEquals(MoonPayPaymentState.Completed("tx-1"), pay().last())
    }

    @Test
    fun `a purchase without an outcome has an unknown outcome`() = runPayment {
        checkout(MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.NoOutcome(null), null))

        assertEquals(MoonPayPaymentState.Unknown(null), pay().last())
    }

    @Test
    fun `a final completion without an id keeps the created one`() = runPayment {
        checkout(
            MoonPayCheckoutEvent.Started(transaction("pending")),
            MoonPayCheckoutEvent.Finished(
                MoonPayTransaction(null, "failed", failureCode = "authorizationDeclined"),
                sessionUpgraded = false,
            ),
        )

        assertEquals(MoonPayPaymentState.Failed(MoonPayPaymentFailure.Declined, "tx-1"), pay().last())
    }

    @Test
    fun `a widget reports success as complete`() = runPayment {
        checkout(MoonPayCheckoutEvent.Finished(transaction("complete"), sessionUpgraded = false))

        assertEquals(MoonPayPaymentState.Completed("tx-1"), pay().last())
    }

    @Test
    fun `a collector may stop on any state`() = runPayment {
        checkout(
            MoonPayCheckoutEvent.Started(transaction("pending")),
            MoonPayCheckoutEvent.Finished(transaction("completed"), sessionUpgraded = false),
        )

        val states = interactor.pay(quote(), theme = null, externalTransactionId = "order-1", interactive = true, commands = emptyFlow()).take(2).toList()

        assertEquals(listOf(MoonPayPaymentState.Starting, MoonPayPaymentState.Started("tx-1")), states)
    }

    @Test
    fun `a failed checkout before a transaction has none`() = runPayment {
        checkout(MoonPayCheckoutEvent.Failed(MoonPayCheckoutError.Cancelled, null))

        assertEquals(MoonPayPaymentState.Failed(MoonPayPaymentFailure.Cancelled, null), pay().last())
    }

    @Test
    fun `a checkout cancelled from outside before the start fails instead of hanging`() = runPayment {
        every { checkout.run(connection, any(), any(), "order-1", any()) } returns flow { throw CancellationException("host left") }

        assertEquals(MoonPayPaymentState.Failed(MoonPayPaymentFailure.Unavailable, null), pay().last())
    }

    @Test
    fun `a checkout that ends without a result before the start fails`() = runPayment {
        checkout()

        assertEquals(MoonPayPaymentState.Failed(MoonPayPaymentFailure.Unavailable, null), pay().last())
    }

    @Test
    fun `a failed transaction is mapped by its failure code`() = runPayment {
        checkout(
            MoonPayCheckoutEvent.Finished(
                MoonPayTransaction("tx-1", "failed", failureCode = "authorizationDeclined"),
                sessionUpgraded = false,
            ),
        )

        assertEquals(MoonPayPaymentState.Failed(MoonPayPaymentFailure.Declined, "tx-1"), pay().last())
    }

    @Test
    fun `a pending transaction is polled until it completes`() = runPayment {
        checkout(MoonPayCheckoutEvent.Finished(transaction("pending"), sessionUpgraded = false))
        polled += listOf(details("pending"), Result.failure(IOException("offline")), details("completed"))

        assertEquals(
            listOf(MoonPayPaymentState.Starting, MoonPayPaymentState.Processing("tx-1"), MoonPayPaymentState.Completed("tx-1")),
            pay(),
        )
        assertEquals(9_000L, testScheduler.currentTime)
    }

    @Test
    fun `a followed transaction is polled to its outcome`() = runPayment {
        polled += listOf(details("pending"), details("completed"))

        assertEquals(
            listOf(MoonPayPaymentState.Processing("tx-1"), MoonPayPaymentState.Completed("tx-1")),
            interactor.follow("tx-1", theme = null).toList(),
        )
    }

    @Test
    fun `a polled failure is mapped by the failed stage`() = runPayment {
        checkout(MoonPayCheckoutEvent.Finished(transaction("pending"), sessionUpgraded = false))
        polled += details(
            "failed",
            listOf(MoonPayTransactionDetails.Stage(name = "payment", status = "failed", failureReason = "rejected_card")),
        )

        assertEquals(MoonPayPaymentState.Failed(MoonPayPaymentFailure.Declined, "tx-1"), pay().last())
    }

    @Test
    fun `after a minute the payment is still processing and polled slower until the deadline`() = runPayment {
        checkout(MoonPayCheckoutEvent.Finished(transaction("pending"), sessionUpgraded = false))
        polled += List(14 + 120) { details("pending") }

        assertEquals(
            listOf(
                MoonPayPaymentState.Starting,
                MoonPayPaymentState.Processing("tx-1"),
                MoonPayPaymentState.StillProcessing("tx-1", pollsUntil = QUICK_MS + WINDOW_MS),
                MoonPayPaymentState.Unknown("tx-1"),
            ),
            pay(),
        )
        assertEquals(QUICK_MS + WINDOW_MS, testScheduler.currentTime)
        coVerify(exactly = 14 + 120) { transactions.transaction("tx-1", any()) }
    }

    @Test
    fun `a payment completed in the slow phase is reported`() = runPayment {
        checkout(MoonPayCheckoutEvent.Finished(transaction("pending"), sessionUpgraded = false))
        polled += List(16) { details("pending") } + details("completed")

        assertEquals(
            listOf(
                MoonPayPaymentState.Starting,
                MoonPayPaymentState.Processing("tx-1"),
                MoonPayPaymentState.StillProcessing("tx-1", pollsUntil = QUICK_MS + WINDOW_MS),
                MoonPayPaymentState.Completed("tx-1"),
            ),
            pay(),
        )
        assertEquals(QUICK_MS + 3 * 15_000L, testScheduler.currentTime)
    }

    @Test
    fun `a refused request stops polling at once`() = runPayment {
        checkout(MoonPayCheckoutEvent.Finished(transaction("pending"), sessionUpgraded = false))
        polled += Result.failure(MoonPayApiException.Http(403, null))

        assertEquals(
            listOf(MoonPayPaymentState.Starting, MoonPayPaymentState.Processing("tx-1"), MoonPayPaymentState.Unknown("tx-1")),
            pay(),
        )
        assertEquals(3_000L, testScheduler.currentTime)
    }

    @Test
    fun `a transaction MoonPay does not serve yet is asked for again in the first minute`() = runPayment {
        checkout(MoonPayCheckoutEvent.Finished(transaction("pending"), sessionUpgraded = false))
        polled += listOf(Result.failure(MoonPayApiException.Http(404, null)), details("completed"))

        assertEquals(MoonPayPaymentState.Completed("tx-1"), pay().last())
        assertEquals(6_000L, testScheduler.currentTime)
    }

    @Test
    fun `a missing transaction stops the slow phase`() = runPayment {
        checkout(MoonPayCheckoutEvent.Finished(transaction("pending"), sessionUpgraded = false))
        polled += List(14) { details("pending") } + Result.failure(MoonPayApiException.Http(404, null))

        assertEquals(MoonPayPaymentState.Unknown("tx-1"), pay().last())
        assertEquals(QUICK_MS + 15_000L, testScheduler.currentTime)
    }

    @Test
    fun `a request MoonPay may answer later is asked again`() = runPayment {
        checkout(MoonPayCheckoutEvent.Finished(transaction("pending"), sessionUpgraded = false))
        polled += listOf(Result.failure(MoonPayApiException.Http(429, null)), details("completed"))

        assertEquals(MoonPayPaymentState.Completed("tx-1"), pay().last())
    }

    @Test
    fun `a refused request in the slow phase stops it`() = runPayment {
        checkout(MoonPayCheckoutEvent.Finished(transaction("pending"), sessionUpgraded = false))
        polled += List(14) { details("pending") } + Result.failure(MoonPayApiException.Http(403, null))

        assertEquals(MoonPayPaymentState.Unknown("tx-1"), pay().last())
        assertEquals(QUICK_MS + 15_000L, testScheduler.currentTime)
    }

    @Test
    fun `a failed silent connect backs off in the slow phase only`() = runPayment {
        checkout(MoonPayCheckoutEvent.Finished(transaction("pending"), sessionUpgraded = false))
        val timedOut = Result.failure<MoonPayTransactionDetails>(MoonPayConnectionException.TimedOut())
        polled += List(14) { timedOut } + timedOut + details("completed")

        assertEquals(MoonPayPaymentState.Completed("tx-1"), pay().last())
        assertEquals(QUICK_MS + 15_000L + 45_000L + 15_000L, testScheduler.currentTime)
    }

    @Test
    fun `a connection MoonPay is still setting up is asked again`() = runPayment {
        checkout(MoonPayCheckoutEvent.Finished(transaction("pending"), sessionUpgraded = false))
        val pending = Result.failure<MoonPayTransactionDetails>(
            MoonPayConnectionException.Rejected(MoonPayConnectionStatus.Pending),
        )
        polled += List(14) { pending } + pending + details("completed")

        assertEquals(MoonPayPaymentState.Completed("tx-1"), pay().last())
        assertEquals(QUICK_MS + 15_000L + 45_000L + 15_000L, testScheduler.currentTime)
    }

    @Test
    fun `a connection that needs signing in stops the slow phase`() = runPayment {
        checkout(MoonPayCheckoutEvent.Finished(transaction("pending"), sessionUpgraded = false))
        polled += List(14) { details("pending") } +
            Result.failure(MoonPayConnectionException.Rejected(MoonPayConnectionStatus.ConnectionRequired))

        assertEquals(MoonPayPaymentState.Unknown("tx-1"), pay().last())
        assertEquals(QUICK_MS + 15_000L, testScheduler.currentTime)
    }

    @Test
    fun `a frame that fails to load backs off in the slow phase`() = runPayment {
        checkout(MoonPayCheckoutEvent.Finished(transaction("pending"), sessionUpgraded = false))
        polled += List(14) { details("pending") } +
            Result.failure(MoonPayFrameException.LoadFailed("offline")) +
            details("completed")

        assertEquals(MoonPayPaymentState.Completed("tx-1"), pay().last())
        assertEquals(QUICK_MS + 15_000L + 45_000L + 15_000L, testScheduler.currentTime)
    }

    @Test
    fun `a failed connection is asked again in the first minute and stops the slow phase`() = runPayment {
        checkout(MoonPayCheckoutEvent.Finished(transaction("pending"), sessionUpgraded = false))
        val failed = Result.failure<MoonPayTransactionDetails>(
            MoonPayConnectionException.Rejected(MoonPayConnectionStatus.Unavailable),
        )
        polled += List(15) { failed }

        assertEquals(MoonPayPaymentState.Unknown("tx-1"), pay().last())
        assertEquals(QUICK_MS + 15_000L, testScheduler.currentTime)
        coVerify(exactly = 15) { transactions.transaction("tx-1", any()) }
    }

    @Test
    fun `a silent connect cancelled by another flow backs off in the slow phase`() = runPayment {
        checkout(MoonPayCheckoutEvent.Finished(transaction("pending"), sessionUpgraded = false))
        polled += List(14) { details("pending") } +
            Result.failure(MoonPayConnectionException.Cancelled()) +
            details("completed")

        assertEquals(MoonPayPaymentState.Completed("tx-1"), pay().last())
        assertEquals(QUICK_MS + 15_000L + 45_000L + 15_000L, testScheduler.currentTime)
    }

    @Test
    fun `the last slow poll waits no longer than the deadline`() = runPayment {
        polled += details("pending")

        val states = interactor.followUntil("tx-1", theme = null, pollsUntil = 2_000L).toList()

        assertEquals(MoonPayPaymentState.Unknown("tx-1"), states.last())
        assertEquals(2_000L, testScheduler.currentTime)
        coVerify(exactly = 1) { transactions.transaction("tx-1", any()) }
    }

    @Test
    fun `past the deadline a failed request is tried again until MoonPay answers`() = runPayment {
        clock = { testScheduler.currentTime + 61_000L }
        polled += listOf(Result.failure(IOException("offline")), details("completed"))

        val states = interactor.followUntil("tx-1", theme = null, pollsUntil = 60_000L).toList()

        assertEquals(MoonPayPaymentState.Completed("tx-1"), states.last())
        assertEquals(5_000L, testScheduler.currentTime)
    }

    @Test
    fun `past the deadline MoonPay gets three tries to answer`() = runPayment {
        clock = { testScheduler.currentTime + 61_000L }

        val states = interactor.followUntil("tx-1", theme = null, pollsUntil = 60_000L).toList()

        assertEquals(MoonPayPaymentState.Unknown("tx-1"), states.last())
        assertEquals(10_000L, testScheduler.currentTime)
        coVerify(exactly = 3) { transactions.transaction("tx-1", any()) }
    }

    @Test
    fun `a back-off ends at the deadline`() = runPayment {
        polled += Result.failure(MoonPayConnectionException.Remote(MoonPayFrameError("e", "frame")))
        polled += details("pending")

        val states = interactor.followUntil("tx-1", theme = null, pollsUntil = 20_000L).toList()

        assertEquals(MoonPayPaymentState.Unknown("tx-1"), states.last())
        assertEquals(20_000L, testScheduler.currentTime)
        coVerify(exactly = 2) { transactions.transaction("tx-1", any()) }
    }

    @Test
    fun `a payment still processing is followed to the same deadline`() = runPayment {
        polled += listOf(details("pending"), details("completed"))

        assertEquals(
            listOf(MoonPayPaymentState.StillProcessing("tx-1", 60_000L), MoonPayPaymentState.Completed("tx-1")),
            interactor.followUntil("tx-1", theme = null, pollsUntil = 60_000L).toList(),
        )
    }

    @Test
    fun `a payment followed past its deadline is checked once at once`() = runPayment {
        clock = { 61_000L }
        polled += details("pending")

        assertEquals(
            listOf(MoonPayPaymentState.StillProcessing("tx-1", 60_000L), MoonPayPaymentState.Unknown("tx-1")),
            interactor.followUntil("tx-1", theme = null, pollsUntil = 60_000L).toList(),
        )
        assertEquals(0L, testScheduler.currentTime)
        coVerify(exactly = 1) { transactions.transaction(any(), any()) }
    }

    @Test
    fun `nothing is asked in the background and coming back asks at once`() = runPayment {
        checkout(MoonPayCheckoutEvent.Finished(transaction("pending"), sessionUpgraded = false))
        polled += List(14) { details("pending") }
        val states = mutableListOf<MoonPayPaymentState>()
        val job = launch { interactor.pay(quote(), theme = null, externalTransactionId = "order-1", interactive = true, commands = emptyFlow()).toList(states) }

        advanceTimeBy(QUICK_MS + 5_000L)
        foreground.value = false
        advanceTimeBy(10 * 60_000L)
        coVerify(exactly = 14) { transactions.transaction("tx-1", any()) }

        polled += details("completed")
        foreground.value = true
        runCurrent()

        assertEquals(MoonPayPaymentState.Completed("tx-1"), states.last())
        assertEquals(QUICK_MS + 5_000L + 10 * 60_000L, testScheduler.currentTime)
        coVerify(exactly = 15) { transactions.transaction("tx-1", any()) }
        job.join()
    }

    @Test
    fun `a deadline passed in the background gets one more check on return`() = runPayment {
        checkout(MoonPayCheckoutEvent.Finished(transaction("pending"), sessionUpgraded = false))
        polled += List(14) { details("pending") }
        val states = mutableListOf<MoonPayPaymentState>()
        val job = launch { interactor.pay(quote(), theme = null, externalTransactionId = "order-1", interactive = true, commands = emptyFlow()).toList(states) }

        advanceTimeBy(QUICK_MS + 5_000L)
        foreground.value = false
        advanceTimeBy(40 * 60_000L)
        polled += details("completed")
        foreground.value = true
        runCurrent()

        assertEquals(MoonPayPaymentState.Completed("tx-1"), states.last())
        coVerify(exactly = 15) { transactions.transaction("tx-1", any()) }
        job.join()
    }

    @Test
    fun `the first minute waits in the background too`() = runPayment {
        foreground.value = false
        checkout(MoonPayCheckoutEvent.Finished(transaction("pending"), sessionUpgraded = false))
        polled += details("completed")
        val states = mutableListOf<MoonPayPaymentState>()
        val job = launch { interactor.pay(quote(), theme = null, externalTransactionId = "order-1", interactive = true, commands = emptyFlow()).toList(states) }

        advanceTimeBy(5 * 60_000L)
        coVerify(exactly = 0) { transactions.transaction(any(), any()) }

        foreground.value = true
        runCurrent()

        assertEquals(MoonPayPaymentState.Completed("tx-1"), states.last())
        job.join()
    }

    @Test
    fun `a transaction without an id is not polled`() = runPayment {
        checkout(MoonPayCheckoutEvent.Finished(MoonPayTransaction(null, "pending"), sessionUpgraded = false))

        assertEquals(listOf(MoonPayPaymentState.Starting, MoonPayPaymentState.Unknown(null)), pay())
        coVerify(exactly = 0) { transactions.transaction(any(), any()) }
    }

    @Test
    fun `an upgraded session drops the cached credentials`() = runPayment {
        checkout(MoonPayCheckoutEvent.Finished(transaction("completed"), sessionUpgraded = true))

        pay()

        coVerify { connections.invalidate() }
    }

    private fun runPayment(body: suspend TestScope.() -> Unit): TestResult = runTest {
        clock = { testScheduler.currentTime }
        body()
    }

    private suspend fun pay(quote: MoonPayBuyQuote = quote()): List<MoonPayPaymentState> {
        return interactor.pay(quote, theme = null, externalTransactionId = "order-1", interactive = true, commands = emptyFlow()).toList()
    }

    private fun checkout(vararg events: MoonPayCheckoutEvent) {
        val flow: Flow<MoonPayCheckoutEvent> = flowOf(*events)
        every { checkout.run(connection, any(), any(), "order-1", any()) } returns flow
    }

    private fun transaction(status: String): MoonPayTransaction = MoonPayTransaction("tx-1", status)

    private fun details(
        status: String,
        stages: List<MoonPayTransactionDetails.Stage> = emptyList(),
    ): Result<MoonPayTransactionDetails> {
        return Result.success(MoonPayTransactionDetails(id = "tx-1", status = status, stages = stages))
    }

    private fun quote(
        executable: Boolean = true,
        expired: Boolean = false,
        signature: String = SIGNATURE,
    ): MoonPayBuyQuote {
        return MoonPayBuyQuote(
            source = MoonPayMoney(amount = "100", asset = MoonPayAsset(code = "usd")),
            destination = MoonPayMoney(amount = "20", asset = MoonPayAsset(code = "ton")),
            paymentMethod = MoonPayPaymentMethodReference(type = "card"),
            executable = executable,
            signature = signature,
            expiresAtMark = if (expired) {
                TestTimeSource().markNow() - 1.seconds
            } else {
                null
            },
        )
    }

    private companion object {
        const val QUICK_MS = 60_000L
        const val WINDOW_MS = 30 * 60_000L
        val SIGNATURE = jwt(
            """
            {"signature":"s","wallet":{"address":"UQ"},"paymentMethod":{"type":"card"},
             "source":{"amount":"100","asset":{"code":"usd"}},"destination":{"amount":"20","asset":{"code":"ton"}}}
            """.trimIndent(),
        )

        fun jwt(payload: String): String {
            return "h.${Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())}.sig"
        }
    }
}
