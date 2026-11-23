package com.tonapps.ledgerkit.app.eth

import com.tonapps.ledgerkit.apdu.Apdu

object EthTransactionFormat {

    private const val RLP_LIST_OFFSET = 0xc0
    private const val MAX_UINT32 = 0xffffffffL

    fun isLegacy(unsignedTransaction: ByteArray): Boolean =
        unsignedTransaction.isNotEmpty() && (unsignedTransaction[0].toInt() and 0xff) >= RLP_LIST_OFFSET

    fun legacySafeChunkSize(totalLength: Int, pathLength: Int, chainId: Long): Int {
        val trailerLength = rlpLengthOfUnsigned(chainId) + 2
        var size = Apdu.MAX_DATA_LENGTH
        while (size > pathLength) {
            val remainder = totalLength % size
            if (remainder == 0 || remainder > trailerLength) {
                return size
            }
            size--
        }
        return Apdu.MAX_DATA_LENGTH
    }

    fun legacyParity(deviceV: Int, chainId: Long): Int {
        var truncatedChainId = chainId
        while (truncatedChainId > MAX_UINT32) {
            truncatedChainId = truncatedChainId shr 8
        }
        val parityZeroV = ((truncatedChainId * 2 + 35) and 0xff).toInt()
        return if (deviceV == parityZeroV) { 0 } else { 1 }
    }

    internal fun rlpLengthOfUnsigned(value: Long): Int = when {
        value in 0..0x7f -> 1
        else -> 1 + (64 - java.lang.Long.numberOfLeadingZeros(value) + 7) / 8
    }
}
