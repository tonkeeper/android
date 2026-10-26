package com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.bridge

import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.Script
import com.tonapps.tonkeeper.ui.screen.browser.dapp.silenceLog
import com.tonapps.tonkeeperx.BuildConfig
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class InAppBridgeChannelTest {

    private val log = EventLog()

    private val transport = FakeTransport(log)

    private var onMainThread = true

    private val channel = InAppBridgeChannel(FakeProtocol(), transport, isMainThread = { onMainThread })

    @Test
    fun `an answer goes to the port of the request it answers`() = runTest {
        channel.attach()
        transport.deliver("r:1", log.reply("port1"))
        transport.deliver("r:2", log.reply("port2"))

        channel.send(FakeAnswer("2", "two"))

        assertEquals(listOf("port2 <- 2:two"), posts())
    }

    @Test
    fun `a request is answered once`() = runTest {
        channel.attach()
        transport.deliver("r:1", log.reply("port1"))

        channel.send(FakeAnswer("1", "first"))
        channel.send(FakeAnswer("1", "second"))

        assertEquals(listOf("port1 <- 1:first"), posts())
    }

    @Test
    fun `an answer to a request never falls back to the session port`() = runTest {
        channel.attach()
        transport.deliver("h", log.reply("session"))

        channel.send(FakeAnswer("7", "unknown"))

        assertEquals(emptyList<String>(), posts())
    }

    @Test
    fun `a session answer goes to the port of the latest handshake`() = runTest {
        channel.attach()
        transport.deliver("h", log.reply("first"))
        transport.deliver("h", log.reply("second"))

        channel.send(FakeAnswer(null, "connected"))

        assertEquals(listOf("second <- null:connected"), posts())
    }

    @Test
    fun `a handshake drops the ports of the previous document`() = runTest {
        channel.attach()
        transport.deliver("r:1", log.reply("old"))
        transport.deliver("h", log.reply("session"))

        channel.send(FakeAnswer("1", "late"))

        assertEquals(emptyList<String>(), posts())
    }

    @Test
    fun `detach drops every port and stops the messages`() = runTest {
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            channel.messages.collect {}
        }
        channel.attach()
        transport.deliver("h", log.reply("session"))
        transport.deliver("r:1", log.reply("port1"))

        channel.detach()
        channel.send(FakeAnswer("1", "late"))
        channel.send(FakeAnswer(null, "late"))

        assertEquals(emptyList<String>(), posts())
        assertTrue(collector.isCompleted)
    }

    @Test
    fun `a script that fails to load fails the attach`() = runTest {
        val failing = InAppBridgeChannel(
            FakeProtocol(Script { error("no bundle") }),
            transport,
            isMainThread = { true },
        )

        assertFalse(failing.attach())
        assertEquals(emptyList<String>(), log.events)
    }

    @Test
    fun `a call off the main thread is dropped`() = runTest {
        val received = collectMessages()
        channel.attach()

        onMainThread = false
        assertDroppedOffMain { transport.deliver("r:1", log.reply("port1")) }
        onMainThread = true
        channel.send(FakeAnswer("1", "answer"))

        transport.deliver("r:2", log.reply("port2"))
        onMainThread = false
        assertDroppedOffMain { channel.send(FakeAnswer("2", "answer")) }

        assertEquals(listOf(FakeMessage("2")), received)
        assertEquals(emptyList<String>(), posts())
    }

    private fun TestScope.collectMessages(): List<FakeMessage> {
        val received = mutableListOf<FakeMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            channel.messages.toList(received)
        }
        return received
    }

    private fun posts(): List<String> = log.events.filter { "<-" in it }

    // Debug builds fail loudly on a call off the main thread; release builds drop it.
    private fun assertDroppedOffMain(call: () -> Unit) {
        val failure = runCatching(call).exceptionOrNull()
        if (BuildConfig.DEBUG) {
            assertTrue(failure is IllegalStateException)
        } else {
            assertNull(failure)
        }
    }

    private companion object {

        @JvmStatic
        @BeforeClass
        fun setUpLog() {
            silenceLog()
        }
    }
}