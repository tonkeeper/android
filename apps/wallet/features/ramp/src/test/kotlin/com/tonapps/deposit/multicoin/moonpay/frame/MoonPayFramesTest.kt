package com.tonapps.deposit.multicoin.moonpay.frame

import com.tonapps.deposit.multicoin.moonpay.api.MoonPayEnvironment
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayFrames.Presentation
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MoonPayFramesTest {

    private val frames = MoonPayFrames()

    private val frame = MoonPayFrame.AddCard(clientToken = "client", theme = null)

    private val host = Any()

    @Before
    fun attachHost() {
        frames.attachHost(host)
    }

    @Test
    fun `a second host is refused until the first leaves`() {
        val second = Any()

        assertFalse(frames.attachHost(second))

        frames.detachHost(second)
        frames.detachHost(host)

        assertTrue(frames.attachHost(second))
    }

    @Test
    fun `a frame waits for a host on screen`() = runTest {
        frames.detachHost(host)
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            frames.run(frame, MoonPayEnvironment.Production).collect {}
        }

        assertEquals(emptyList<MoonPayFrames.Entry>(), frames.state.value.entries)

        frames.attachHost(host)

        assertEquals(listOf(frame), frames.state.value.entries.map { it.frame })
        job.cancel()
    }

    @Test
    fun `a host leaving the screen fails its frames`() = runTest {
        var failure: Throwable? = null
        launch(UnconfinedTestDispatcher(testScheduler)) {
            runCatching {
                frames.run(frame, MoonPayEnvironment.Production).collect {}
            }.onFailure { failure = it }
        }

        frames.detachHost(host)

        assertTrue(failure is MoonPayFrameException.HostGone)
        assertEquals(emptyList<MoonPayFrames.Entry>(), frames.state.value.entries)
    }

    @Test
    fun `a host leaving empties the list before the flows finish`() = runTest {
        launch { runCatching { frames.run(frame, MoonPayEnvironment.Production).collect {} } }
        runCurrent()

        frames.detachHost(host)

        assertEquals(emptyList<MoonPayFrames.Entry>(), frames.state.value.entries)
        val second = Any()
        assertTrue(frames.attachHost(second))
        assertEquals(emptyList<MoonPayFrames.Entry>(), frames.state.value.entries)
        runCurrent()
    }

    @Test
    fun `a frame is not added when its host leaves before it lands`() = runTest {
        frames.detachHost(host)
        val job = launch { frames.run(frame, MoonPayEnvironment.Production).collect {} }
        runCurrent()

        frames.attachHost(host)
        frames.detachHost(host)
        runCurrent()

        assertEquals(emptyList<MoonPayFrames.Entry>(), frames.state.value.entries)
        assertTrue(job.isActive)

        frames.attachHost(host)
        runCurrent()

        assertEquals(listOf(frame), frames.state.value.entries.map { it.frame })
        job.cancel()
    }

    @Test
    fun `a frame stops listening once its collection is cancelled`() = runTest {
        val job = launch { frames.run(frame, MoonPayEnvironment.Production).collect {} }
        runCurrent()
        val entry = frames.state.value.entries.single()

        assertTrue(entry.isListening)

        job.cancel()
        runCurrent()

        assertFalse(entry.isListening)
    }

    @Test
    fun `a closed frame leaves the list at once`() = runTest {
        launch { frames.run(frame, MoonPayEnvironment.Production).collect {} }
        runCurrent()

        frames.state.value.entries.single().onClose()

        assertEquals(emptyList<MoonPayFrames.Entry>(), frames.state.value.entries)
        runCurrent()
    }

    @Test
    fun `a frame is shown while its events are collected`() = runTest {
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            frames.run(frame, MoonPayEnvironment.Production).collect {}
        }

        val entry = frames.state.value.entries.single()
        assertEquals(frame, entry.frame)
        assertEquals(Presentation.Modal, entry.presentation)

        job.cancel()

        assertEquals(emptyList<MoonPayFrames.Entry>(), frames.state.value.entries)
    }

    @Test
    fun `events arrive in order and closing completes the flow`() = runTest {
        val events = mutableListOf<MoonPayFrameEvent>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            frames.run(frame, MoonPayEnvironment.Production).toList(events)
        }
        val entry = frames.state.value.entries.single()

        entry.onEvent(MoonPayFrameEvent.Handshake)
        entry.onEvent(MoonPayFrameEvent.Ready)
        entry.onClose()

        assertTrue(job.isCompleted)
        assertEquals(listOf(MoonPayFrameEvent.Handshake, MoonPayFrameEvent.Ready), events)
        assertEquals(emptyList<MoonPayFrames.Entry>(), frames.state.value.entries)
    }

    @Test
    fun `a load failure fails the flow`() = runTest {
        var failure: Throwable? = null
        launch(UnconfinedTestDispatcher(testScheduler)) {
            runCatching {
                frames.run(frame, MoonPayEnvironment.Production).collect {}
            }.onFailure { failure = it }
        }

        frames.state.value.entries.single().onFailure(MoonPayFrameException.LoadFailed("offline"))

        assertTrue(failure is MoonPayFrameException.LoadFailed)
        assertEquals(emptyList<MoonPayFrames.Entry>(), frames.state.value.entries)
    }

    @Test
    fun `frames are listed in the order they opened`() = runTest {
        val challenge = MoonPayFrame.Challenge(url = "https://platform.moonpay.com/c", clientToken = "client", theme = null, resume = false)
        val first = launch(UnconfinedTestDispatcher(testScheduler)) {
            frames.run(frame, MoonPayEnvironment.Production).collect {}
        }
        val second = launch(UnconfinedTestDispatcher(testScheduler)) {
            frames.run(challenge, MoonPayEnvironment.Production).collect {}
        }

        assertEquals(listOf(frame, challenge), frames.state.value.entries.map { it.frame })

        first.cancel()

        assertEquals(listOf(challenge), frames.state.value.entries.map { it.frame })
        second.cancel()
    }
}
