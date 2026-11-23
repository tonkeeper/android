package com.tonapps.wallet.data.ledger.solana

import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException

internal class SolanaWireTransaction private constructor(
    private val signatures: List<ByteArray>,
    val message: ByteArray,
    private val signers: List<ByteArray>,
) {

    fun signerIndex(publicKey: ByteArray): Int {
        val index = signers.indexOfFirst { it.contentEquals(publicKey) }
        if (index < 0) {
            throw LedgerFailureException(LedgerFailure.InvalidPayload(0, "The transaction is not signed by this account"))
        }
        return index
    }

    fun withSignature(index: Int, signature: ByteArray): ByteArray {
        require(signature.size == SIGNATURE_LENGTH) { "Solana signature must be $SIGNATURE_LENGTH bytes" }
        val updated = signatures.toMutableList().apply { set(index, signature) }
        return encodeShortVec(updated.size) + updated.reduce(ByteArray::plus) + message
    }

    companion object {

        const val SIGNATURE_LENGTH = 64
        private const val PUBLIC_KEY_LENGTH = 32
        private const val VERSION_PREFIX = 0x80
        private const val HEADER_LENGTH = 3

        fun parse(bytes: ByteArray): SolanaWireTransaction {
            val reader = SolanaByteReader(bytes)
            val signatureCount = reader.shortVec()
            val signatures = List(signatureCount) { reader.bytes(SIGNATURE_LENGTH) }
            val message = bytes.copyOfRange(reader.position, bytes.size)
            val messageReader = SolanaByteReader(message)
            if (messageReader.peek() and VERSION_PREFIX != 0) {
                messageReader.byte()
            }
            val header = messageReader.bytes(HEADER_LENGTH)
            val requiredSignatures = header[0].toInt() and 0xff
            val keyCount = messageReader.shortVec()
            if (requiredSignatures != signatureCount || requiredSignatures > keyCount) {
                throw SolanaByteReader.malformed()
            }
            val signers = List(requiredSignatures) { messageReader.bytes(PUBLIC_KEY_LENGTH) }
            return SolanaWireTransaction(signatures, message, signers)
        }

        fun encodeShortVec(value: Int): ByteArray {
            require(value in 0..0xffff) { "Solana compact length out of range" }
            val out = mutableListOf<Byte>()
            var rest = value
            do {
                var byte = rest and 0x7f
                rest = rest ushr 7
                if (rest != 0) {
                    byte = byte or 0x80
                }
                out += byte.toByte()
            } while (rest != 0)
            return out.toByteArray()
        }
    }
}
