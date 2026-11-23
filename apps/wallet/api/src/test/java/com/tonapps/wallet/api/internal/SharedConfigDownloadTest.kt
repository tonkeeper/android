package com.tonapps.wallet.api.internal

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class SharedConfigDownloadTest {

    private var downloads = 0

    @Test
    fun waitersWhileADownloadIsInFlightShareIt() = runTest {
        val downloaded = CompletableDeferred<Boolean>()
        val shared = sharedDownload { downloaded.await() }

        val first = async { shared.awaitDownloaded() }
        val second = async { shared.awaitDownloaded() }
        runCurrent()
        downloaded.complete(true)

        assertTrue(first.await())
        assertTrue(second.await())
        assertEquals(1, downloads)
    }

    @Test
    fun aWaiterJoinsTheDownloadARefreshStarted() = runTest {
        val downloaded = CompletableDeferred<Boolean>()
        val shared = sharedDownload { downloaded.await() }

        val refresh = async { shared.refresh() }
        runCurrent()
        val waiter = async { shared.awaitDownloaded() }
        runCurrent()
        downloaded.complete(true)

        assertTrue(refresh.await())
        assertTrue(waiter.await())
        assertEquals(1, downloads)
    }

    @Test
    fun aWaiterWhoseDownloadFailsJoinsTheOneARefreshStartedMeanwhile() = runTest {
        val outcomes = listOf(CompletableDeferred<Boolean>(), CompletableDeferred<Boolean>())
        val shared = sharedDownload { outcomes[downloads - 1].await() }

        val waiter = async { shared.awaitDownloaded() }
        runCurrent()
        val refresh = async { shared.refresh() }
        runCurrent()
        outcomes[0].complete(false)
        runCurrent()
        assertFalse(waiter.isCompleted)

        outcomes[1].complete(true)

        assertTrue(waiter.await())
        assertTrue(refresh.await())
        assertEquals(2, downloads)
    }

    @Test
    fun onceAConfigIsDownloadedAWaiterDoesNotDownloadAgain() = runTest {
        val shared = sharedDownload { true }

        assertTrue(shared.awaitDownloaded())
        assertTrue(shared.awaitDownloaded())
        assertEquals(1, downloads)
    }

    @Test
    fun afterAFailedDownloadAWaiterDownloadsAgainOnlyOnceTheRetryDelayHasPassed() = runTest {
        var reachable = false
        val shared = sharedDownload { reachable }
        assertFalse(shared.awaitDownloaded())

        reachable = true
        advanceTimeBy(RETRY_DELAY - 1.milliseconds)
        assertFalse(shared.awaitDownloaded())
        assertEquals(1, downloads)

        advanceTimeBy(1.milliseconds)
        assertTrue(shared.awaitDownloaded())
        assertEquals(2, downloads)
    }

    @Test
    fun aDownloadThatFailsAfterItsWaiterWasCancelledStillDelaysTheRetry() = runTest {
        val downloaded = CompletableDeferred<Boolean>()
        val shared = sharedDownload { downloaded.await() }
        val waiter = async { shared.awaitDownloaded() }
        runCurrent()

        waiter.cancel()
        downloaded.complete(false)
        runCurrent()

        assertFalse(shared.awaitDownloaded())
        assertEquals(1, downloads)
    }

    @Test
    fun aRefreshDownloadsAgainWithoutWaitingOutTheRetryDelay() = runTest {
        val shared = sharedDownload { false }

        assertFalse(shared.awaitDownloaded())
        assertFalse(shared.refresh())
        assertEquals(2, downloads)
    }

    @Test
    fun aDownloadThatThrowsDelaysTheRetryLikeAFailedOne() = runTest {
        val shared = sharedDownload { throw IOException("disk full") }
        val failure = try {
            shared.awaitDownloaded()
            null
        } catch (e: IOException) {
            e
        }
        assertNotNull(failure)

        assertFalse(shared.awaitDownloaded())
        assertEquals(1, downloads)
    }

    private fun TestScope.sharedDownload(download: suspend () -> Boolean) = SharedConfigDownload(
        scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        retryDelay = RETRY_DELAY,
        timeSource = testScheduler.timeSource,
        download = {
            downloads++
            download()
        },
    )

    private companion object {
        val RETRY_DELAY = 5.seconds
    }
}
