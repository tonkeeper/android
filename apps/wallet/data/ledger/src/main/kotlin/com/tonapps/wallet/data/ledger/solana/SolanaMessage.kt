package com.tonapps.wallet.data.ledger.solana

import com.tonapps.ledgerkit.util.Base58

internal class SolanaMessage private constructor(
    private val accountKeys: List<ByteArray>,
    val instructions: List<Instruction>,
) {

    fun address(index: Int): String? = accountKeys.getOrNull(index)?.let(Base58::encode)

    fun program(instruction: Instruction): String? = address(instruction.programIndex)

    class Instruction(val programIndex: Int, val accounts: List<Int>, val data: ByteArray)

    companion object {

        private const val VERSION_PREFIX = 0x80
        private const val HEADER_LENGTH = 3
        private const val PUBLIC_KEY_LENGTH = 32
        private const val BLOCKHASH_LENGTH = 32

        fun parse(message: ByteArray): SolanaMessage {
            val reader = SolanaByteReader(message)
            if (reader.peek() and VERSION_PREFIX != 0) {
                reader.byte()
            }
            reader.bytes(HEADER_LENGTH)
            val accountKeys = List(reader.shortVec()) { reader.bytes(PUBLIC_KEY_LENGTH) }
            reader.bytes(BLOCKHASH_LENGTH)
            val instructions = List(reader.shortVec()) {
                val programIndex = reader.byte()
                val accounts = List(reader.shortVec()) { reader.byte() }
                Instruction(programIndex, accounts, reader.bytes(reader.shortVec()))
            }
            return SolanaMessage(accountKeys, instructions)
        }
    }
}
