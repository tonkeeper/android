package com.tonapps.ledgerkit.apdu

object Apdu {

    const val MAX_DATA_LENGTH = 255
    private const val HEADER_LENGTH = 5
    private const val STATUS_WORD_LENGTH = 2

    fun build(cla: Int, ins: Int, p1: Int, p2: Int, data: ByteArray = ByteArray(0)): ByteArray {
        require(data.size <= MAX_DATA_LENGTH) { "APDU data length ${data.size} exceeds $MAX_DATA_LENGTH" }
        val apdu = ByteArray(HEADER_LENGTH + data.size)
        apdu[0] = cla.toByte()
        apdu[1] = ins.toByte()
        apdu[2] = p1.toByte()
        apdu[3] = p2.toByte()
        apdu[4] = data.size.toByte()
        data.copyInto(apdu, HEADER_LENGTH)
        return apdu
    }

    fun parseResponse(raw: ByteArray): ApduResponse {
        require(raw.size >= STATUS_WORD_LENGTH) { "Truncated APDU response of ${raw.size} bytes" }
        val statusWord = ((raw[raw.size - 2].toInt() and 0xff) shl 8) or (raw[raw.size - 1].toInt() and 0xff)
        return ApduResponse(raw.copyOfRange(0, raw.size - STATUS_WORD_LENGTH), statusWord)
    }
}

class ApduResponse(val data: ByteArray, val statusWord: Int) {

    val isOk: Boolean
        get() = statusWord == StatusWord.OK

    fun toRaw(): ByteArray = data + byteArrayOf((statusWord shr 8).toByte(), statusWord.toByte())
}
