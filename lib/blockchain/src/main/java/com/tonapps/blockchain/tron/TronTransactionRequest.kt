package com.tonapps.blockchain.tron

sealed interface TronTransactionRequest {

    val from: String
    val feeLimit: Long

    /** The memo the transaction carries as its `raw_data.data`, hex; null for none. */
    val memo: String?

    data class Trx(
        override val from: String,
        val to: String,
        val amountSun: Long,
        override val memo: String? = null,
    ) : TronTransactionRequest {

        override val feeLimit: Long = 0
    }

    data class Trc20(
        val transfer: TronTransfer,
        override val feeLimit: Long,
        val callValue: Long = 0,
        override val memo: String? = null,
    ) : TronTransactionRequest {

        override val from: String = transfer.from
    }

    /** A call of [contractAddress] with [callData] as it stands: hex, without `0x`. */
    data class Call(
        override val from: String,
        val contractAddress: String,
        val callData: String,
        override val feeLimit: Long,
        val callValue: Long = 0,
        override val memo: String? = null,
    ) : TronTransactionRequest
}
