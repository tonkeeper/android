package com.tonapps.wallet.data.ledger.solana

import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException

internal class SolanaByteReader(private val bytes: ByteArray) {

    var position = 0
        private set

    fun peek(): Int {
        if (position >= bytes.size) {
            throw malformed()
        }
        return bytes[position].toInt() and 0xff
    }

    fun byte(): Int = peek().also { position++ }

    fun bytes(count: Int): ByteArray {
        if (count < 0 || position + count > bytes.size) {
            throw malformed()
        }
        return bytes.copyOfRange(position, position + count).also { position += count }
    }

    fun shortVec(): Int {
        var value = 0
        var shift = 0
        repeat(MAX_SHORT_VEC_BYTES) {
            val byte = byte()
            value = value or ((byte and 0x7f) shl shift)
            if (byte and 0x80 == 0) {
                return value
            }
            shift += 7
        }
        throw malformed()
    }

    companion object {

        private const val MAX_SHORT_VEC_BYTES = 3

        fun malformed() = LedgerFailureException(LedgerFailure.InvalidPayload(0, "Malformed Solana transaction"))
    }
}
