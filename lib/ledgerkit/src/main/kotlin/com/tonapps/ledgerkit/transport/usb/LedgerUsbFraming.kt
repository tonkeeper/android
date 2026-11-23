package com.tonapps.ledgerkit.transport.usb

import com.tonapps.ledgerkit.transport.ble.LedgerFramingException

object LedgerUsbFraming {

    const val PACKET_SIZE = 64
    const val CHANNEL = 0x0001
    const val MAX_APDU_LENGTH = 0xffff

    private const val TAG_APDU: Byte = 0x05
    private const val FIRST_HEADER_LENGTH = 7
    private const val NEXT_HEADER_LENGTH = 5

    fun split(apdu: ByteArray, channel: Int = CHANNEL, packetSize: Int = PACKET_SIZE): List<ByteArray> {
        require(packetSize > FIRST_HEADER_LENGTH) { "Packet size $packetSize is too small" }
        require(apdu.size <= MAX_APDU_LENGTH) { "APDU of ${apdu.size} bytes cannot be framed" }
        val packets = mutableListOf<ByteArray>()
        var offset = 0
        var index = 0
        do {
            val headerLength = if (index == 0) { FIRST_HEADER_LENGTH } else { NEXT_HEADER_LENGTH }
            val bodyLength = minOf(apdu.size - offset, packetSize - headerLength)
            val packet = ByteArray(packetSize)
            packet[0] = (channel shr 8).toByte()
            packet[1] = channel.toByte()
            packet[2] = TAG_APDU
            packet[3] = (index shr 8).toByte()
            packet[4] = index.toByte()
            if (index == 0) {
                packet[5] = (apdu.size shr 8).toByte()
                packet[6] = apdu.size.toByte()
            }
            apdu.copyInto(packet, headerLength, offset, offset + bodyLength)
            packets += packet
            offset += bodyLength
            index++
        } while (offset < apdu.size)
        return packets
    }

    internal fun headerLength(index: Int): Int = if (index == 0) { FIRST_HEADER_LENGTH } else { NEXT_HEADER_LENGTH }

    internal fun isApduTag(tag: Byte): Boolean = tag == TAG_APDU
}

class LedgerUsbReassembler(private val channel: Int = LedgerUsbFraming.CHANNEL) {

    private var expectedLength = -1
    private var nextIndex = 0
    private var buffer = ByteArray(0)
    private var received = 0

    fun reset() {
        expectedLength = -1
        nextIndex = 0
        buffer = ByteArray(0)
        received = 0
    }

    fun accept(packet: ByteArray): ByteArray? {
        val headerLength = LedgerUsbFraming.headerLength(nextIndex)
        if (packet.size < headerLength) {
            throw LedgerFramingException("Truncated packet of ${packet.size} bytes")
        }
        val packetChannel = ((packet[0].toInt() and 0xff) shl 8) or (packet[1].toInt() and 0xff)
        if (packetChannel != channel) {
            throw LedgerFramingException("Unexpected channel $packetChannel")
        }
        if (!LedgerUsbFraming.isApduTag(packet[2])) {
            throw LedgerFramingException("Unexpected packet tag")
        }
        val index = ((packet[3].toInt() and 0xff) shl 8) or (packet[4].toInt() and 0xff)
        if (index != nextIndex) {
            throw LedgerFramingException("Unexpected packet index $index, expected $nextIndex")
        }
        if (index == 0) {
            expectedLength = ((packet[5].toInt() and 0xff) shl 8) or (packet[6].toInt() and 0xff)
            buffer = ByteArray(expectedLength)
            received = 0
        }
        val bodyLength = minOf(packet.size - headerLength, expectedLength - received)
        packet.copyInto(buffer, received, headerLength, headerLength + bodyLength)
        received += bodyLength
        nextIndex++
        if (received < expectedLength) {
            return null
        }
        val complete = buffer
        reset()
        return complete
    }
}
