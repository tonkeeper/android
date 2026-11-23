package com.tonapps.ledgerkit.transport

import com.tonapps.ledgerkit.transport.ble.LedgerBleFraming
import com.tonapps.ledgerkit.transport.ble.LedgerBleReassembler
import com.tonapps.ledgerkit.transport.ble.LedgerFramingException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LedgerBleFramingTest {

    @Test
    fun `single frame carries tag index and total length`() {
        val apdu = byteArrayOf(0xe0.toByte(), 0x02, 0x00, 0x00, 0x00)
        val frames = LedgerBleFraming.split(apdu, frameSize = 20)
        assertEquals(1, frames.size)
        assertArrayEquals(byteArrayOf(0x05, 0x00, 0x00, 0x00, 0x05) + apdu, frames[0])
    }

    @Test
    fun `multi frame split uses five byte first header and three byte next headers`() {
        val apdu = ByteArray(40) { it.toByte() }
        val frames = LedgerBleFraming.split(apdu, frameSize = 20)
        assertEquals(3, frames.size)
        assertArrayEquals(byteArrayOf(0x05, 0x00, 0x00, 0x00, 40) + apdu.copyOfRange(0, 15), frames[0])
        assertArrayEquals(byteArrayOf(0x05, 0x00, 0x01) + apdu.copyOfRange(15, 32), frames[1])
        assertArrayEquals(byteArrayOf(0x05, 0x00, 0x02) + apdu.copyOfRange(32, 40), frames[2])
    }

    @Test
    fun `split and reassemble round trip for many sizes`() {
        for (frameSize in listOf(20, 23, 153, 156, 244)) {
            for (length in listOf(1, 2, 14, 15, 16, 150, 151, 152, 500, 4096, 65535)) {
                val payload = ByteArray(length) { (it * 7 + length).toByte() }
                val reassembler = LedgerBleReassembler()
                var result: ByteArray? = null
                LedgerBleFraming.split(payload, frameSize).forEach { frame ->
                    assertEquals(true, frame.size <= frameSize)
                    result = reassembler.accept(frame) ?: result
                }
                assertArrayEquals("frameSize=$frameSize length=$length", payload, result)
            }
        }
    }

    @Test
    fun `reassembler returns null until the declared length arrives`() {
        val reassembler = LedgerBleReassembler()
        assertNull(reassembler.accept(byteArrayOf(0x05, 0x00, 0x00, 0x00, 0x04, 0x01, 0x02)))
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x90.toByte(), 0x00), reassembler.accept(byteArrayOf(0x05, 0x00, 0x01, 0x90.toByte(), 0x00)))
    }

    @Test
    fun `reassembler resets after a complete response`() {
        val reassembler = LedgerBleReassembler()
        reassembler.accept(byteArrayOf(0x05, 0x00, 0x00, 0x00, 0x02, 0x90.toByte(), 0x00))
        assertArrayEquals(byteArrayOf(0x69, 0x85.toByte()), reassembler.accept(byteArrayOf(0x05, 0x00, 0x00, 0x00, 0x02, 0x69, 0x85.toByte())))
    }

    @Test(expected = LedgerFramingException::class)
    fun `out of order frame is rejected`() {
        val reassembler = LedgerBleReassembler()
        reassembler.accept(byteArrayOf(0x05, 0x00, 0x00, 0x00, 0x10, 0x01))
        reassembler.accept(byteArrayOf(0x05, 0x00, 0x02, 0x01))
    }

    @Test(expected = LedgerFramingException::class)
    fun `wrong tag is rejected`() {
        LedgerBleReassembler().accept(byteArrayOf(0x02, 0x00, 0x00, 0x00, 0x01, 0x01))
    }

    @Test
    fun `mtu handshake reply exposes frame size`() {
        assertEquals(153, LedgerBleFraming.parseMtuHandshake(byteArrayOf(0x08, 0, 0, 0, 1, 153.toByte())))
        assertNull(LedgerBleFraming.parseMtuHandshake(byteArrayOf(0x08, 0, 0, 0, 1)))
        assertNull(LedgerBleFraming.parseMtuHandshake(byteArrayOf(0x08, 0, 0, 0, 1, 5)))
    }

    @Test
    fun `model is resolved from any of its service uuids`() {
        assertEquals(LedgerModel.Stax, LedgerModel.fromServiceUuid(java.util.UUID.fromString("13d63400-2c97-6004-0000-4c6564676572")))
        assertEquals(LedgerModel.NanoGen5, LedgerModel.fromServiceUuid(java.util.UUID.fromString("13d63400-2c97-9004-0000-4c6564676572")))
        assertEquals(LedgerModel.Flex, LedgerModel.fromServiceUuid(java.util.UUID.fromString("13d63400-2c97-3004-0000-4c6564676572")))
        assertNull(LedgerModel.fromServiceUuid(java.util.UUID.fromString("13d63400-2c97-1234-0000-4c6564676572")))
    }
}
