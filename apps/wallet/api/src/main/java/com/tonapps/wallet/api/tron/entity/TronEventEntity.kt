package com.tonapps.wallet.api.tron.entity

import com.tonapps.icu.Coins
import com.tonapps.wallet.api.entity.TokenEntity
import io.batteryapi.models.TronTransactionsListTransactionsInner
import org.json.JSONObject

data class TronEventEntity(
    val amount: Coins,
    val timestamp: Long,
    val transactionHash: String,
    val from: String,
    val to: String,
    val isFailed: Boolean = false,
    val inProgress: Boolean = false,
    val batteryCharges: Int? = null
) {
    constructor(json: JSONObject) : this(
        amount = Coins.ofNano(json.getString("value"), decimals = TokenEntity.TRON_USDT.decimals),
        timestamp = json.getLong("block_timestamp") / 1000,
        transactionHash = json.getString("transaction_id"),
        from = json.getString("from"),
        to = json.getString("to"),
    )

    constructor(transaction: TronTransactionsListTransactionsInner) : this(
        amount = Coins.ofNano(transaction.amount, decimals = TokenEntity.TRON_USDT.decimals),
        timestamp = transaction.timestamp,
        transactionHash = transaction.txid,
        from = transaction.fromAccount,
        to = transaction.toAccount,
        isFailed = transaction.isFailed,
        inProgress = transaction.isPending,
        batteryCharges = transaction.batteryCharges
    )
}
