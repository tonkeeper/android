package com.tonapps.ledgerkit.controller

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.session.FakeLedgerDevice
import com.tonapps.ledgerkit.session.LedgerAppInfo
import com.tonapps.ledgerkit.transport.LedgerLinkKind
import com.tonapps.ledgerkit.transport.LedgerModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class LedgerControllerTest {

    private val request = LedgerRequest(deviceId = "AA:BB", deviceName = "Stax", purpose = LedgerPurpose.SignTransaction)

    private var now = 0L

    private fun TestScope.controller() = LedgerController(
        transport = FakeLedgerDevice(),
        scope = backgroundScope,
        idleTimeout = 1.seconds,
        doneHold = 1.milliseconds,
        clock = { now },
    )

    private fun TestScope.results(controller: LedgerController): List<LedgerOperationResult> {
        val collected = mutableListOf<LedgerOperationResult>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { controller.results.toList(collected) }
        return collected
    }

    private fun <T> TestScope.start(block: suspend () -> T): CompletableDeferred<Result<T>> {
        val outcome = CompletableDeferred<Result<T>>()
        backgroundScope.launch {
            val result = try {
                Result.success(block())
            } catch (e: LedgerFailureException) {
                Result.failure(e)
            }
            outcome.complete(result)
        }
        return outcome
    }

    private fun Result<*>.ledgerFailure(): LedgerFailureException = exceptionOrNull() as LedgerFailureException

    @Test
    fun `non ledger exception becomes a retryable unexpected failure`() = runTest {
        val controller = controller()
        val run = start { controller.run(request) { throw IllegalArgumentException("bad typed data") } }
        runCurrent()
        val state = controller.state.value as LedgerOperationState.Failed
        assertTrue(state.failure is LedgerFailure.Unexpected)
        assertTrue(state.canRetry)
        controller.dismiss()
        val error = run.await().ledgerFailure()
        assertTrue(error.failure is LedgerFailure.Unexpected)
        assertTrue(error.isPresented)
        assertEquals(LedgerOperationState.Idle, controller.state.value)
    }

    @Test
    fun `retry runs the block again after a failure`() = runTest {
        val controller = controller()
        var attempts = 0
        val run = start {
            controller.run(request) {
                attempts++
                if (attempts == 1) {
                    throw LedgerFailureException(LedgerFailure.DeviceLocked)
                }
                "signed"
            }
        }
        runCurrent()
        assertEquals(LedgerFailure.DeviceLocked, (controller.state.value as LedgerOperationState.Failed).failure)
        controller.retry()
        assertEquals("signed", run.await().getOrThrow())
        assertEquals(2, attempts)
    }

    @Test
    fun `second request while one is running is busy and not presented`() = runTest {
        val controller = controller()
        val gate = CompletableDeferred<Unit>()
        val first = start { controller.run(request) { gate.await() } }
        runCurrent()
        val second = start { controller.run(request) { Unit } }
        runCurrent()
        val error = second.await().ledgerFailure()
        assertEquals(LedgerFailure.Busy, error.failure)
        assertFalse(error.isPresented)
        gate.complete(Unit)
        assertTrue(first.await().isSuccess)
    }

    @Test
    fun `cancel while active ends with a presented cancellation`() = runTest {
        val controller = controller()
        val gate = CompletableDeferred<Unit>()
        val run = start { controller.run(request) { gate.await() } }
        runCurrent()
        assertTrue(controller.state.value is LedgerOperationState.Active)
        controller.cancel()
        val error = run.await().ledgerFailure()
        assertEquals(LedgerFailure.Cancelled, error.failure)
        assertTrue(error.isPresented)
        assertEquals(LedgerOperationState.Idle, controller.state.value)
    }

    @Test
    fun `open link reports the parked session until the idle timeout closes it`() = runTest {
        val controller = controller()
        assertEquals(null, controller.openLink())
        val run = start { controller.run(request) { session.currentApp() } }
        run.await().getOrThrow()
        assertEquals(LedgerOpenLink("AA:BB", LedgerAppInfo("BOLOS", "1.2.3")), controller.openLink())
        advanceTimeBy(2.seconds)
        runCurrent()
        assertEquals(null, controller.openLink())
    }

    @Test
    fun `closing the idle session drops the open link`() = runTest {
        val controller = controller()
        start { controller.run(request) { session.currentApp() } }.await().getOrThrow()
        controller.closeIdleSession()
        assertEquals(null, controller.openLink())
    }

    @Test
    fun `a request that succeeds reports one attempt with the device it talked to`() = runTest {
        val controller = controller()
        val results = results(controller)
        start {
            controller.run(request) {
                ensureApp(LedgerAppId.Ton)
                now += 1_500
            }
        }.await().getOrThrow()
        val result = results.single()
        assertEquals(LedgerOperationOutcome.Success, result.outcome)
        assertEquals(1, result.attempts)
        assertEquals(1_500L, result.durationMs)
        assertNull(result.lastFailure)
        assertEquals(LedgerAppId.Ton, result.app)
        assertEquals(LedgerModel.Stax, result.model)
        assertEquals(LedgerLinkKind.Bluetooth, result.linkKind)
    }

    @Test
    fun `a request retried after a failure reports the attempts and the failure it got past`() = runTest {
        val controller = controller()
        val results = results(controller)
        var tries = 0
        val run = start {
            controller.run(request) {
                tries++
                if (tries == 1) {
                    throw LedgerFailureException(LedgerFailure.DeviceLocked)
                }
            }
        }
        runCurrent()
        assertTrue(results.isEmpty())
        controller.retry()
        run.await().getOrThrow()
        val result = results.single()
        assertEquals(LedgerOperationOutcome.Success, result.outcome)
        assertEquals(2, result.attempts)
        assertEquals(LedgerFailure.DeviceLocked, result.lastFailure)
        assertEquals(listOf(LedgerFailure.DeviceLocked), result.errors.map { it.failure })
    }

    @Test
    fun `a failure the user closes reports a failed request`() = runTest {
        val controller = controller()
        val results = results(controller)
        val refused = LedgerFailure.UserRefused(0x6985)
        val run = start { controller.run(request) { throw LedgerFailureException(refused) } }
        runCurrent()
        controller.dismiss()
        run.await()
        val result = results.single()
        assertEquals(LedgerOperationOutcome.Failed, result.outcome)
        assertEquals(refused, result.lastFailure)
        assertNull(result.model)
        assertNull(result.linkKind)
    }

    @Test
    fun `a request cancelled in the app or by its caller reports a cancellation`() = runTest {
        val controller = controller()
        val results = results(controller)
        val gate = CompletableDeferred<Unit>()
        val cancelled = start { controller.run(request) { gate.await() } }
        runCurrent()
        controller.cancel()
        cancelled.await()
        val caller = backgroundScope.launch { controller.run(request) { gate.await() } }
        runCurrent()
        caller.cancel()
        runCurrent()
        assertEquals(listOf(LedgerOperationOutcome.Cancelled, LedgerOperationOutcome.Cancelled), results.map { it.outcome })
        assertTrue(results.all { it.errors.isEmpty() })
    }

    @Test
    fun `a request refused as busy reports nothing`() = runTest {
        val controller = controller()
        val results = results(controller)
        val gate = CompletableDeferred<Unit>()
        val first = start { controller.run(request) { gate.await() } }
        runCurrent()
        start { controller.run(request) { Unit } }.await()
        assertTrue(results.isEmpty())
        gate.complete(Unit)
        first.await()
        assertEquals(1, results.size)
    }
}
