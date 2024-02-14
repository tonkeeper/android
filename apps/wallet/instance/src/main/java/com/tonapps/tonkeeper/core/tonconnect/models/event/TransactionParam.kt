package com.tonapps.tonkeeper.core.tonconnect.models.event

import org.json.JSONObject
import org.ton.contract.wallet.WalletTransfer

data class TransactionParam(
    val messages: List<TransactionMessage>,
    val validUntil: Long,
    val from: String? = null,
    val network: String? = null
) {

    constructor(json: JSONObject) : this(
        json.getJSONArray("messages").let { 0.until(it.length()).map { i -> it.getJSONObject(i) } }.map { TransactionMessage(it) },
        json.getLong("valid_until"),
        json.optString("from"),
        json.optString("com/tonapps/network")
    )

    constructor(data: String) : this(JSONObject(data))

    fun createWalletTransfers(): List<WalletTransfer> {
        return messages.map { it.createWalletTransfer() }
    }
}
