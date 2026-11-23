package com.tonapps.wallet.data.ledger.signing

import com.tonapps.ledgerkit.LedgerFailure
import com.tonapps.ledgerkit.LedgerFailureException
import com.trustwallet.core.CoinType
import com.trustwallet.core.DataVector
import com.trustwallet.core.TransactionCompiler
import com.trustwallet.core.common.SigningError
import com.trustwallet.core.transactioncompiler.PreSigningOutput

internal object WalletCoreCompiler {

    fun preImage(coin: CoinType, input: ByteArray): ByteArray {
        val output = PreSigningOutput.ADAPTER.decode(TransactionCompiler.preImageHashes(coin, input))
        if (output.error != SigningError.OK) {
            throw LedgerFailureException(LedgerFailure.InvalidPayload(0, output.error_message.ifBlank { output.error.name }))
        }
        return output.data_.toByteArray()
    }

    fun compile(coin: CoinType, input: ByteArray, signature: ByteArray): ByteArray {
        val signatures = DataVector().apply { add(signature) }
        return TransactionCompiler.compileWithSignatures(coin, input, signatures, DataVector())
    }

    fun requireSigned(error: SigningError, message: String) {
        if (error != SigningError.OK) {
            throw LedgerFailureException(LedgerFailure.InvalidPayload(0, message.ifBlank { error.name }))
        }
    }
}

internal fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

internal fun String.hexToBytes(): ByteArray {
    val clean = removePrefix("0x").removePrefix("0X")
    require(clean.length % 2 == 0) { "Odd-length hex" }
    return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

internal fun String.isHexEncoded(): Boolean {
    val clean = removePrefix("0x").removePrefix("0X")
    return clean.isNotEmpty() && clean.length % 2 == 0 && clean.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
}
