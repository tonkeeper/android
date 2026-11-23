package com.tonapps.ledgerkit.app.btc

import com.tonapps.ledgerkit.apdu.Bip32Path

data class BtcSpendInput(
    val previousTxidInternal: ByteArray,
    val previousIndex: Long,
    val amount: Long,
    val scriptPubKey: ByteArray,
    val publicKey: ByteArray,
    val path: Bip32Path,
    val previousTransaction: ByteArray?,
    val sequence: Long = DEFAULT_SEQUENCE,
) {
    companion object {
        const val DEFAULT_SEQUENCE = 0xffffffffL
    }
}

data class BtcSpendOutput(
    val amount: Long,
    val script: ByteArray,
    val ownPublicKey: ByteArray? = null,
    val ownPath: Bip32Path? = null,
)

class BtcUnsignedTransaction(
    val inputs: List<BtcSpendInput>,
    val outputs: List<BtcSpendOutput>,
    val version: Long = 2,
    val lockTime: Long = 0,
) {

    val fee: Long
        get() = inputs.sumOf { it.amount } - outputs.sumOf { it.amount }

    fun finalize(signatures: Map<Int, ByteArray>): BtcRawTransaction {
        require(signatures.keys == inputs.indices.toSet()) { "Every input must be signed" }
        return BtcRawTransaction(
            version = version,
            inputs = inputs.map { BtcRawTransaction.Input(it.previousTxidInternal, it.previousIndex, ByteArray(0), it.sequence) },
            outputs = outputs.map { BtcRawTransaction.Output(it.amount, it.script) },
            witnesses = inputs.mapIndexed { index, input -> listOf(signatures.getValue(index), input.publicKey) },
            lockTime = lockTime,
        )
    }
}

class BtcPsbtV2(
    val global: BtcMerkleizedMap,
    val inputs: List<BtcMerkleizedMap>,
    val outputs: List<BtcMerkleizedMap>,
) {

    companion object {

        private const val GLOBAL_TX_VERSION = 0x02
        private const val GLOBAL_FALLBACK_LOCKTIME = 0x03
        private const val GLOBAL_INPUT_COUNT = 0x04
        private const val GLOBAL_OUTPUT_COUNT = 0x05
        private const val GLOBAL_VERSION = 0xfb
        private const val IN_NON_WITNESS_UTXO = 0x00
        private const val IN_WITNESS_UTXO = 0x01
        private const val IN_BIP32_DERIVATION = 0x06
        private const val IN_PREVIOUS_TXID = 0x0e
        private const val IN_OUTPUT_INDEX = 0x0f
        private const val IN_SEQUENCE = 0x10
        private const val OUT_BIP32_DERIVATION = 0x02
        private const val OUT_AMOUNT = 0x03
        private const val OUT_SCRIPT = 0x04
        private const val PSBT_VERSION = 2L

        fun build(transaction: BtcUnsignedTransaction, masterFingerprint: ByteArray): BtcPsbtV2 {
            require(masterFingerprint.size == 4) { "Master fingerprint must be 4 bytes" }
            val global = mapOf(
                key(GLOBAL_TX_VERSION) to littleEndian(transaction.version, 4),
                key(GLOBAL_FALLBACK_LOCKTIME) to littleEndian(transaction.lockTime, 4),
                key(GLOBAL_INPUT_COUNT) to CompactSize.encode(transaction.inputs.size),
                key(GLOBAL_OUTPUT_COUNT) to CompactSize.encode(transaction.outputs.size),
                key(GLOBAL_VERSION) to littleEndian(PSBT_VERSION, 4),
            )
            val inputs = transaction.inputs.map { input ->
                buildMap {
                    input.previousTransaction?.let { put(key(IN_NON_WITNESS_UTXO), it) }
                    put(
                        key(IN_WITNESS_UTXO),
                        littleEndian(input.amount, 8) + CompactSize.encode(input.scriptPubKey.size) + input.scriptPubKey,
                    )
                    put(key(IN_BIP32_DERIVATION, input.publicKey), derivation(masterFingerprint, input.path))
                    put(key(IN_PREVIOUS_TXID), input.previousTxidInternal)
                    put(key(IN_OUTPUT_INDEX), littleEndian(input.previousIndex, 4))
                    put(key(IN_SEQUENCE), littleEndian(input.sequence, 4))
                }
            }
            val outputs = transaction.outputs.map { output ->
                buildMap {
                    put(key(OUT_AMOUNT), littleEndian(output.amount, 8))
                    put(key(OUT_SCRIPT), output.script)
                    if (output.ownPublicKey != null && output.ownPath != null) {
                        put(key(OUT_BIP32_DERIVATION, output.ownPublicKey), derivation(masterFingerprint, output.ownPath))
                    }
                }
            }
            return BtcPsbtV2(BtcMerkleizedMap(global), inputs.map(::BtcMerkleizedMap), outputs.map(::BtcMerkleizedMap))
        }

        private fun key(type: Int, data: ByteArray = ByteArray(0)): ByteArrayKey = ByteArrayKey(byteArrayOf(type.toByte()) + data)

        private fun derivation(masterFingerprint: ByteArray, path: Bip32Path): ByteArray =
            path.indices.fold(masterFingerprint) { accumulator, index -> accumulator + littleEndian(index, 4) }
    }
}
