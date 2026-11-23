package com.tonapps.ledgerkit.transport

import com.tonapps.ledgerkit.transport.ble.LedgerFramingException
import com.tonapps.ledgerkit.transport.usb.LedgerUsbFraming
import com.tonapps.ledgerkit.transport.usb.LedgerUsbIds
import com.tonapps.ledgerkit.transport.usb.LedgerUsbReassembler
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerUsbFramingTest {

    @Test
    fun `single packet carries channel tag index and length and is padded to the report size`() {
        val apdu = byteArrayOf(0xe0.toByte(), 0x01, 0x00, 0x00, 0x00)
        val packets = LedgerUsbFraming.split(apdu)
        assertEquals(1, packets.size)
        val expected = ByteArray(64)
        byteArrayOf(0x00, 0x01, 0x05, 0x00, 0x00, 0x00, 0x05).copyInto(expected)
        apdu.copyInto(expected, 7)
        assertArrayEquals(expected, packets[0])
    }

    @Test
    fun `long apdu uses a seven byte first header and five byte next headers`() {
        val apdu = ByteArray(130) { it.toByte() }
        val packets = LedgerUsbFraming.split(apdu)
        assertEquals(3, packets.size)
        assertArrayEquals(byteArrayOf(0x00, 0x01, 0x05, 0x00, 0x00, 0x00, 130.toByte()), packets[0].copyOfRange(0, 7))
        assertArrayEquals(apdu.copyOfRange(0, 57), packets[0].copyOfRange(7, 64))
        assertArrayEquals(byteArrayOf(0x00, 0x01, 0x05, 0x00, 0x01), packets[1].copyOfRange(0, 5))
        assertArrayEquals(apdu.copyOfRange(57, 116), packets[1].copyOfRange(5, 64))
        assertArrayEquals(byteArrayOf(0x00, 0x01, 0x05, 0x00, 0x02), packets[2].copyOfRange(0, 5))
        assertArrayEquals(apdu.copyOfRange(116, 130), packets[2].copyOfRange(5, 19))
        assertTrue(packets.all { it.size == 64 })
    }

    @Test
    fun `split and reassemble round trip for many sizes`() {
        for (length in listOf(1, 2, 56, 57, 58, 116, 117, 255, 260, 4096, 65535)) {
            val payload = ByteArray(length) { (it * 13 + length).toByte() }
            val reassembler = LedgerUsbReassembler()
            var result: ByteArray? = null
            LedgerUsbFraming.split(payload).forEach { packet -> result = reassembler.accept(packet) ?: result }
            assertArrayEquals("length=$length", payload, result)
        }
    }

    @Test
    fun `reassembler waits for the declared length and then resets`() {
        val reassembler = LedgerUsbReassembler()
        val response = ByteArray(70) { it.toByte() } + byteArrayOf(0x90.toByte(), 0x00)
        val packets = LedgerUsbFraming.split(response)
        assertNull(reassembler.accept(packets[0]))
        assertArrayEquals(response, reassembler.accept(packets[1]))
        val next = byteArrayOf(0x69, 0x85.toByte())
        assertArrayEquals(next, reassembler.accept(LedgerUsbFraming.split(next)[0]))
    }

    @Test(expected = LedgerFramingException::class)
    fun `reassembler rejects another channel`() {
        LedgerUsbReassembler().accept(LedgerUsbFraming.split(byteArrayOf(0x90.toByte(), 0x00), channel = 0x0101)[0])
    }

    @Test(expected = LedgerFramingException::class)
    fun `reassembler rejects a packet out of order`() {
        val packets = LedgerUsbFraming.split(ByteArray(100))
        LedgerUsbReassembler().accept(packets[1])
    }

    @Test(expected = LedgerFramingException::class)
    fun `reassembler rejects a non apdu tag`() {
        val packet = LedgerUsbFraming.split(byteArrayOf(0x90.toByte(), 0x00))[0]
        packet[2] = 0x08
        LedgerUsbReassembler().accept(packet)
    }

    @Test
    fun `usb product ids map to models for legacy and current firmware`() {
        assertEquals(LedgerModel.NanoS, LedgerModel.fromUsbProductId(0x0001))
        assertEquals(LedgerModel.NanoX, LedgerModel.fromUsbProductId(0x0004))
        assertEquals(LedgerModel.NanoSPlus, LedgerModel.fromUsbProductId(0x0005))
        assertEquals(LedgerModel.Stax, LedgerModel.fromUsbProductId(0x0006))
        assertEquals(LedgerModel.Flex, LedgerModel.fromUsbProductId(0x0007))
        assertEquals(LedgerModel.NanoGen5, LedgerModel.fromUsbProductId(0x0008))
        assertEquals(LedgerModel.NanoS, LedgerModel.fromUsbProductId(0x1011))
        assertEquals(LedgerModel.NanoX, LedgerModel.fromUsbProductId(0x4015))
        assertEquals(LedgerModel.NanoSPlus, LedgerModel.fromUsbProductId(0x5011))
        assertEquals(LedgerModel.Stax, LedgerModel.fromUsbProductId(0x6011))
        assertEquals(LedgerModel.Flex, LedgerModel.fromUsbProductId(0x7015))
        assertEquals(LedgerModel.NanoGen5, LedgerModel.fromUsbProductId(0x8011))
        assertNull(LedgerModel.fromUsbProductId(0x0000))
        assertNull(LedgerModel.fromUsbProductId(0x2011))
    }

    @Test
    fun `usb device ids carry the model and never look like a bluetooth address`() {
        assertEquals("usb:NanoSPlus", LedgerUsbIds.deviceId(LedgerModel.NanoSPlus))
        assertEquals(LedgerModel.Stax, LedgerUsbIds.model(LedgerUsbIds.deviceId(LedgerModel.Stax)))
        assertTrue(LedgerUsbIds.isUsb(LedgerUsbIds.deviceId(null)))
        assertNull(LedgerUsbIds.model(LedgerUsbIds.deviceId(null)))
        assertFalse(LedgerUsbIds.isUsb("C1:2B:3C:4D:5E:6F"))
        assertNull(LedgerUsbIds.model("C1:2B:3C:4D:5E:6F"))
    }

    @Test
    fun `usb wins when a ledger is plugged in and usb ids always go over usb`() {
        assertTrue(LedgerUsbIds.routesToUsb("C1:2B:3C:4D:5E:6F", isUsbAttached = true))
        assertFalse(LedgerUsbIds.routesToUsb("C1:2B:3C:4D:5E:6F", isUsbAttached = false))
        assertTrue(LedgerUsbIds.routesToUsb("usb:Stax", isUsbAttached = false))
    }

    @Test
    fun `only bluetooth models advertise bluetooth services`() {
        assertFalse(LedgerModel.NanoS.supportsBluetooth)
        assertFalse(LedgerModel.NanoSPlus.supportsBluetooth)
        assertTrue(LedgerModel.Stax.supportsBluetooth)
    }
}
