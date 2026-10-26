package com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.bridge

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.tonapps.tonkeeper.ui.screen.browser.dapp.silenceLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InAppBridgeConnectionTest {

    private val log = EventLog()

    private val owner = ResumedOwner()

    @Before
    fun setUp() {
        // The connection collects on lifecycleScope, which runs on Main.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        owner.destroy()
        Dispatchers.resetMain()
    }

    @Test
    fun `detach answers what the handler still owes before the transport goes`() = runTest {
        val transport = FakeTransport(log)
        val connection = connection(transport, FakeHandler(owed = listOf(FakeAnswer("1", "cancelled"))))
        connection.attach(owner)
        transport.deliver("r:1", log.reply("port1"))

        connection.detach()

        assertEquals(listOf("transport.attach", "port1 <- 1:cancelled", "transport.detach"), log.events)
    }

    @Test
    fun `a failed attach does not start the handler`() = runTest {
        val handler = FakeHandler()
        val connection = connection(FakeTransport(log, attachSucceeds = false), handler)

        assertFalse(connection.attach(owner))
        connection.detach()

        assertEquals(0, handler.attachCalls)
        assertEquals(0, handler.detachCalls)
        assertEquals(listOf("transport.attach"), log.events)
    }

    @Test
    fun `detach of a connection that never attached does nothing`() = runTest {
        val handler = FakeHandler(owed = listOf(FakeAnswer("1", "cancelled")))
        val connection = connection(FakeTransport(log), handler)

        connection.detach()

        assertEquals(0, handler.detachCalls)
        assertEquals(emptyList<String>(), log.events)
    }

    @Test
    fun `messages reach the handler and its responses reach the page`() = runTest {
        val transport = FakeTransport(log)
        val handler = FakeHandler(echo = true)
        val connection = connection(transport, handler)

        assertTrue(connection.attach(owner))
        transport.deliver("r:7", log.reply("port7"))

        assertEquals(listOf(FakeMessage("7")), handler.received)
        assertEquals(listOf("transport.attach", "port7 <- 7:echo"), log.events)
    }

    private fun connection(
        transport: FakeTransport,
        handler: FakeHandler,
    ): InAppBridgeConnection<FakeMessage, FakeAnswer> {
        return InAppBridgeConnection(InAppBridgeChannel(FakeProtocol(), transport, isMainThread = { true }), handler)
    }

    private class ResumedOwner : LifecycleOwner {

        private val registry = LifecycleRegistry.createUnsafe(this).apply {
            currentState = Lifecycle.State.RESUMED
        }

        override val lifecycle: Lifecycle = registry

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
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