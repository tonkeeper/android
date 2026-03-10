package com.tonapps.tonkeeper.manager.tx

import org.json.JSONObject

data class TransactionEvent(
    val accountId: String,
    val lt: Long,
    val hash: String
) {

    constructor(json: JSONObject) : this(
        accountId = json.getString("account_id"),
        lt = json.getLong("lt"),
        hash = json.getString("tx_hash")
    )
}