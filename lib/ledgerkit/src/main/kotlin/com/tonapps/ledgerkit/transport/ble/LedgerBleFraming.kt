package com.tonapps.ledgerkit.transport.ble

object LedgerBleFraming {

    const val TAG_APDU: Byte = 0x05
    const val TAG_MTU: Byte = 0x08
    const val MIN_FRAME_SIZE = 20
    const val MAX_APDU_LENGTH = 0xffff

    private const val FIRST_HEADER_LENGTH = 5
    private const val NEXT_HEADER_LENGTH = 3

    val mtuHandshakeRequest: ByteArray = byteArrayOf(TAG_MTU, 0, 0, 0, 0)

    fun frame(apdu: ByteArray, index: Int, frameSize: Int): ByteArray? {
        require(frameSize > FIRST_HEADER_LENGTH) { "Frame size $frameSize is too small" }
        require(apdu.size <= MAX_APDU_LENGTH) { "APDU of ${apdu.size} bytes cannot be framed" }
        val firstBody = frameSize - FIRST_HEADER_LENGTH
        val nextBody = frameSize - NEXT_HEADER_LENGTH
        val offset = if (index == 0) { 0 } else { firstBody + (index - 1) * nextBody }
        if (index > 0 && offset >= apdu.size) {
            return null
        }
        val bodyLength = minOf(apdu.size - offset, if (index == 0) { firstBody } else { nextBody })
        val headerLength = if (index == 0) { FIRST_HEADER_LENGTH } else { NEXT_HEADER_LENGTH }
        val frame = ByteArray(headerLength + bodyLength)
        frame[0] = TAG_APDU
        frame[1] = (index shr 8).toByte()
        frame[2] = index.toByte()
        if (index == 0) {
            frame[3] = (apdu.size shr 8).toByte()
            frame[4] = apdu.size.toByte()
        }
        apdu.copyInto(frame, headerLength, offset, offset + bodyLength)
        return frame
    }

    fun split(apdu: ByteArray, frameSize: Int): List<ByteArray> = generateSequence(0) { it + 1 }
        .map { frame(apdu, it, frameSize) }
        .takeWhile { it != null }
        .filterNotNull()
        .toList()

    fun parseMtuHandshake(packet: ByteArray): Int? {
        if (packet.size < 6 || packet[0] != TAG_MTU) {
            return null
        }
        val size = packet[5].toInt() and 0xff
        return size.takeIf { it >= MIN_FRAME_SIZE }
    }
}

class LedgerBleReassembler {

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
        if (packet.size < 3 || packet[0] != LedgerBleFraming.TAG_APDU) {
            throw LedgerFramingException("Unexpected frame tag")
        }
        val index = ((packet[1].toInt() and 0xff) shl 8) or (packet[2].toInt() and 0xff)
        if (index != nextIndex) {
            throw LedgerFramingException("Unexpected frame index $index, expected $nextIndex")
        }
        val headerLength = if (index == 0) {
            if (packet.size < 5) {
                throw LedgerFramingException("Truncated first frame")
            }
            expectedLength = ((packet[3].toInt() and 0xff) shl 8) or (packet[4].toInt() and 0xff)
            buffer = ByteArray(expectedLength)
            received = 0
            5
        } else {
            3
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

class LedgerFramingException(message: String) : IllegalStateException(message)
