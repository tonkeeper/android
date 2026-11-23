package com.tonapps.ledgerkit.app.tron

import com.tonapps.ledgerkit.LedgerAppId
import com.tonapps.ledgerkit.LedgerAppSetting
import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException

internal object TronTransactionChunker {

    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH_DELIMITED = 2
    private const val WIRE_FIXED32 = 5

    fun chunk(pathBytes: ByteArray, rawData: ByteArray): List<ByteArray> {
        val chunks = mutableListOf<ByteArray>()
        var current = pathBytes
        var offset = 0
        while (offset < rawData.size) {
            val fieldLength = fieldLength(rawData, offset)
            if (fieldLength > TronApp.CHUNK_LIMIT) {
                throw LedgerFailureException(LedgerFailure.SettingRequired(LedgerAppId.Tron, LedgerAppSetting.SignByHash))
            }
            if (current.size + fieldLength > TronApp.CHUNK_LIMIT) {
                chunks += current
                current = ByteArray(0)
            }
            current += rawData.copyOfRange(offset, offset + fieldLength)
            offset += fieldLength
        }
        chunks += current
        return chunks
    }

    fun fieldLength(data: ByteArray, start: Int): Int {
        var offset = start
        val (tag, tagLength) = readVarint(data, offset)
        offset += tagLength
        when ((tag and 0x07L).toInt()) {
            WIRE_VARINT -> offset += readVarint(data, offset).second
            WIRE_FIXED64 -> offset += 8
            WIRE_FIXED32 -> offset += 4
            WIRE_LENGTH_DELIMITED -> {
                val (length, lengthBytes) = readVarint(data, offset)
                val available = (data.size - offset - lengthBytes).toLong()
                if (length < 0 || length > available) {
                    throw LedgerFailureException(LedgerFailure.InvalidPayload(0, "Truncated Tron transaction"))
                }
                offset += lengthBytes + length.toInt()
            }
            else -> throw LedgerFailureException(LedgerFailure.InvalidPayload(0, "Unsupported protobuf wire type"))
        }
        if (offset > data.size || offset <= start) {
            throw LedgerFailureException(LedgerFailure.InvalidPayload(0, "Truncated Tron transaction"))
        }
        return offset - start
    }

    private fun readVarint(data: ByteArray, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var offset = start
        while (offset < data.size) {
            val byte = data[offset++].toInt() and 0xff
            result = result or ((byte and 0x7f).toLong() shl shift)
            if (byte and 0x80 == 0) {
                return result to (offset - start)
            }
            shift += 7
            if (shift > 63) {
                break
            }
        }
        throw LedgerFailureException(LedgerFailure.InvalidPayload(0, "Malformed protobuf varint"))
    }
}
