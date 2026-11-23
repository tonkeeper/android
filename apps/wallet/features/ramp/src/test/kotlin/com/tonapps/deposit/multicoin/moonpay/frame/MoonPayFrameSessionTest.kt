package com.tonapps.deposit.multicoin.moonpay.frame

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MoonPayFrameSessionTest {

    private val events = mutableListOf<MoonPayFrameEvent>()

    private val session = MoonPayFrameSession(events::add)

    @Test
    fun `a handshake is acked and still reported`() = runTest {
        val commands = mutableListOf<MoonPayFrameCommand>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { session.responses.toList(commands) }

        session.onMessage(MoonPayFrameEvent.Handshake)
        session.onMessage(MoonPayFrameEvent.Ready)

        assertEquals(listOf(MoonPayFrameCommand.Ack), commands)
        assertEquals(listOf(MoonPayFrameEvent.Handshake, MoonPayFrameEvent.Ready), events)
    }

    @Test
    fun `commands are sent in order`() = runTest {
        val commands = mutableListOf<MoonPayFrameCommand>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { session.responses.toList(commands) }

        session.onMessage(MoonPayFrameEvent.Handshake)
        session.send(MoonPayFrameCommand.SetQuote("sig"))

        assertEquals(listOf(MoonPayFrameCommand.Ack, MoonPayFrameCommand.SetQuote("sig")), commands)
    }

    @Test
    fun `commands sent before the handshake follow its ack`() = runTest {
        val commands = mutableListOf<MoonPayFrameCommand>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { session.responses.toList(commands) }

        session.send(MoonPayFrameCommand.SetQuote("early"))

        assertEquals(emptyList<MoonPayFrameCommand>(), commands)

        session.onMessage(MoonPayFrameEvent.Handshake)

        assertEquals(listOf(MoonPayFrameCommand.Ack, MoonPayFrameCommand.SetQuote("early")), commands)
    }

    @Test
    fun `a command after detach goes nowhere`() = runTest {
        val commands = mutableListOf<MoonPayFrameCommand>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { session.responses.toList(commands) }
        session.onMessage(MoonPayFrameEvent.Handshake)

        assertEquals(emptyList<MoonPayFrameCommand>(), session.detach())
        session.send(MoonPayFrameCommand.SetQuote("sig"))

        assertEquals(listOf(MoonPayFrameCommand.Ack), commands)
    }
}
