package com.tonapps.wallet.data.dapps.confirm

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfirmSheetQueueTest {

    private val queue = ConfirmSheetQueue()

    @Test
    fun `the next request waits until the current sheet is destroyed`() = runTest {
        queue.enqueue("a")
        queue.enqueue("b")

        assertEquals("a", queue.current.first())

        queue.onSheetDestroyed("a")

        assertEquals("b", queue.current.first())
    }

    @Test
    fun `a request already queued is not shown twice`() = runTest {
        queue.enqueue("a")
        queue.enqueue("b")
        queue.enqueue("a")
        queue.enqueue("b")

        queue.onSheetDestroyed("a")
        queue.onSheetDestroyed("b")

        assertNull(queue.current.first())
    }

    @Test
    fun `a destroyed sheet of another request does not move the queue`() = runTest {
        queue.enqueue("a")
        queue.enqueue("b")

        queue.onSheetDestroyed("b")

        assertEquals("a", queue.current.first())
    }

    @Test
    fun `a discarded waiting request is never shown`() = runTest {
        queue.enqueue("a")
        queue.enqueue("b")
        queue.enqueue("c")

        queue.discard("b")
        queue.onSheetDestroyed("a")

        assertEquals("c", queue.current.first())
    }

    @Test
    fun `discarding the current request keeps it until its sheet is destroyed`() = runTest {
        queue.enqueue("a")
        queue.enqueue("b")

        queue.discard("a")

        assertEquals("a", queue.current.first())
    }

    @Test
    fun `only the current sheet is reported destroyed`() = runTest {
        queue.enqueue("a")
        queue.enqueue("b")
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val a = backgroundScope.launch(dispatcher) { queue.awaitSheetDestroyed("a") }
        val b = backgroundScope.launch(dispatcher) { queue.awaitSheetDestroyed("b") }

        queue.onSheetDestroyed("b")
        queue.onSheetDestroyed("a")

        assertTrue(a.isCompleted)
        assertTrue(b.isActive)
    }
}