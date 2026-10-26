package com.tonapps.blockchain.tron

sealed interface TronTransactionRequest {

    val from: String
    val feeLimit: Long

    data class Trx(
        override val from: String,
        val to: String,
        val amountSun: Long,
    ) : TronTransactionRequest {

        override val feeLimit: Long = 0
    }

    data class Trc20(
        val transfer: TronTransfer,
        override val feeLimit: Long,
        val callValue: Long = 0,
    ) : TronTransactionRequest {

        override val from: String = transfer.from
    }
}
