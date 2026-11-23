package com.tonapps.ledgerkit.transport.usb

import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.tonapps.ledgerkit.LedgerPhase
import com.tonapps.ledgerkit.transport.LedgerLinkKind
import com.tonapps.ledgerkit.transport.LedgerModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class LedgerUsbLinkTest {

    private class FakeDevicePort(private val reply: (ByteArray) -> ByteArray?) : LedgerUsbPort {

        val received = CopyOnWriteArrayList<ByteArray>()
        val pending = LinkedBlockingQueue<ByteArray>()
        private val reassembler = LedgerUsbReassembler()

        @Volatile
        var unplugged = false

        @Volatile
        var closeCount = 0

        override fun write(packet: ByteArray): Boolean {
            if (unplugged) {
                return false
            }
            val apdu = reassembler.accept(packet) ?: return true
            received += apdu
            reply(apdu)?.let { response -> LedgerUsbFraming.split(response).forEach(pending::put) }
            return true
        }

        override fun read(buffer: ByteArray, timeout: Duration): Int {
            if (unplugged) {
                return -1
            }
            val packet = pending.poll(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS) ?: return -1
            packet.copyInto(buffer)
            return packet.size
        }

        override fun close() {
            closeCount++
        }
    }

    private fun link(port: LedgerUsbPort) = LedgerUsbLink("usb:Stax", LedgerModel.Stax, port, Dispatchers.IO)

    private suspend fun LedgerUsbLink.failure(apdu: ByteArray, timeout: Duration? = null): LedgerFailure = try {
        exchange(apdu, timeout)
        fail("exchange should fail")
        error("unreachable")
    } catch (e: LedgerFailureException) {
        e.failure
    }

    @Test
    fun `exchange frames the command and parses a multi packet reply`() = runBlocking {
        val body = ByteArray(150) { it.toByte() }
        val port = FakeDevicePort { body + byteArrayOf(0x90.toByte(), 0x00) }
        val link = link(port)
        val command = ByteArray(200) { (it * 3).toByte() }
        val response = link.exchange(command, 5.seconds)
        assertArrayEquals(command, port.received.single())
        assertArrayEquals(body, response.data)
        assertEquals(0x9000, response.statusWord)
        assertEquals(LedgerLinkKind.Usb, link.kind)
        assertTrue(link.isConnected)
    }

    @Test
    fun `waits for a reply that takes longer than one read slice`() = runBlocking {
        val port = FakeDevicePort { null }
        val link = link(port)
        val answer = async { link.exchange(byteArrayOf(0xe0.toByte(), 0x04, 0x00, 0x00, 0x00), timeout = null) }
        delay(700.milliseconds)
        LedgerUsbFraming.split(byteArrayOf(0x69, 0x85.toByte())).forEach(port.pending::put)
        assertEquals(0x6985, withTimeout(5.seconds) { answer.await() }.statusWord)
    }

    @Test
    fun `a reply that never comes times out and closes the link`() = runBlocking {
        val port = FakeDevicePort { null }
        val link = link(port)
        assertEquals(LedgerFailure.Timeout(LedgerPhase.Apdu), link.failure(byteArrayOf(0x01), 400.milliseconds))
        assertFalse(link.isConnected)
        assertEquals(1, port.closeCount)
    }

    @Test
    fun `an unplugged device is reported as a lost connection`() = runBlocking {
        val port = FakeDevicePort { null }
        val link = link(port)
        val answer = async { link.failure(byteArrayOf(0x01)) }
        delay(300.milliseconds)
        port.unplugged = true
        assertEquals(LedgerFailure.ConnectionLost, withTimeout(5.seconds) { answer.await() })
        assertFalse(link.isConnected)
        withTimeout(1.seconds) { link.awaitDisconnected() }
    }

    @Test
    fun `detach marks the link lost and the next exchange fails fast`() = runBlocking {
        val port = FakeDevicePort { byteArrayOf(0x90.toByte(), 0x00) }
        val link = link(port)
        link.markLost()
        withTimeout(1.seconds) { link.awaitDisconnected() }
        assertEquals(LedgerFailure.ConnectionLost, link.failure(byteArrayOf(0x01)))
        assertTrue(port.received.isEmpty())
    }

    @Test
    fun `cancelling a pending exchange closes the link so a late reply is never read as the next one`() = runBlocking {
        val port = FakeDevicePort { null }
        val link = link(port)
        val answer = async { link.exchange(byteArrayOf(0x01), timeout = null) }
        delay(300.milliseconds)
        answer.cancel()
        delay(600.milliseconds)
        assertFalse(link.isConnected)
        assertEquals(1, port.closeCount)
    }

    @Test
    fun `close while a read is in flight releases the port exactly once`() = runBlocking {
        val port = FakeDevicePort { null }
        val link = link(port)
        val answer = async { link.failure(byteArrayOf(0x01)) }
        delay(100.milliseconds)
        link.close()
        assertEquals(LedgerFailure.ConnectionLost, withTimeout(5.seconds) { answer.await() })
        link.close()
        assertEquals(1, port.closeCount)
    }

    @Test
    fun `garbage from the device becomes an unexpected failure`() = runBlocking {
        val port = FakeDevicePort { null }
        val link = link(port)
        port.pending.put(ByteArray(64) { 0x7f })
        assertTrue(link.failure(byteArrayOf(0x01), 2.seconds) is LedgerFailure.Unexpected)
        assertFalse(link.isConnected)
    }
}
