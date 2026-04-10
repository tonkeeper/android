package com.tonapps.wallet.api.entity.toncenter

import org.json.JSONObject

sealed class ToncenterEvent(
    val type: String,
    val finality: String,
) {

    companion object {

        fun parse(json: JSONObject): ToncenterEvent? {
            val type = json.optString("type")
            val finality = json.optString("finality", "")
            return when (type) {
                "transactions" -> TransactionsEvent(json, finality)
                "actions" -> ActionsEvent(json, finality)
                "trace" -> TraceEvent(json, finality)
                "account_state_change" -> AccountStateEvent(json, finality)
                "jettons_change" -> JettonsEvent(json, finality)
                "trace_invalidated" -> TraceInvalidatedEvent(json)
                else -> null
            }
        }
    }
}

class TransactionsEvent(
    val json: JSONObject,
    finality: String,
) : ToncenterEvent("transactions", finality) {

    val traceExternalHashNorm: String?
        get() = json.optString("trace_external_hash_norm").takeIf { it.isNotEmpty() }

    val transactions: org.json.JSONArray?
        get() = json.optJSONArray("transactions")

    val addressBook: JSONObject?
        get() = json.optJSONObject("address_book")

    val metadata: JSONObject?
        get() = json.optJSONObject("metadata")
}

class ActionsEvent(
    val json: JSONObject,
    finality: String,
) : ToncenterEvent("actions", finality) {

    val traceExternalHashNorm: String?
        get() = json.optString("trace_external_hash_norm").takeIf { it.isNotEmpty() }

    val actions: org.json.JSONArray?
        get() = json.optJSONArray("actions")

    val addressBook: JSONObject?
        get() = json.optJSONObject("address_book")

    val metadata: JSONObject?
        get() = json.optJSONObject("metadata")
}

class TraceEvent(
    val json: JSONObject,
    finality: String,
) : ToncenterEvent("trace", finality) {

    val traceExternalHashNorm: String?
        get() = json.optString("trace_external_hash_norm").takeIf { it.isNotEmpty() }

    val trace: JSONObject?
        get() = json.optJSONObject("trace")

    val transactions: JSONObject?
        get() = json.optJSONObject("transactions")

    val actions: org.json.JSONArray?
        get() = json.optJSONArray("actions")

    val addressBook: JSONObject?
        get() = json.optJSONObject("address_book")

    val metadata: JSONObject?
        get() = json.optJSONObject("metadata")
}

class AccountStateEvent(
    val json: JSONObject,
    finality: String,
) : ToncenterEvent("account_state_change", finality) {

    val account: String
        get() = json.optString("account", "")

    val state: AccountState?
        get() = json.optJSONObject("state")?.let { AccountState(it) }
}

data class AccountState(
    val hash: String,
    val balance: String,
    val accountStatus: String,
    val dataHash: String,
    val codeHash: String,
) {

    constructor(json: JSONObject) : this(
        hash = json.optString("hash", ""),
        balance = json.optString("balance", ""),
        accountStatus = json.optString("account_status", ""),
        dataHash = json.optString("data_hash", ""),
        codeHash = json.optString("code_hash", ""),
    )
}

class JettonsEvent(
    val json: JSONObject,
    finality: String,
) : ToncenterEvent("jettons_change", finality) {

    val jetton: JettonChange?
        get() = json.optJSONObject("jetton")?.let { JettonChange(it) }

    val addressBook: JSONObject?
        get() = json.optJSONObject("address_book")

    val metadata: JSONObject?
        get() = json.optJSONObject("metadata")
}

data class JettonChange(
    val address: String,
    val balance: String,
    val owner: String,
    val jetton: String,
    val lastTransactionLt: String,
) {

    constructor(json: JSONObject) : this(
        address = json.optString("address", ""),
        balance = json.optString("balance", ""),
        owner = json.optString("owner", ""),
        jetton = json.optString("jetton", ""),
        lastTransactionLt = json.optString("last_transaction_lt", ""),
    )
}

class TraceInvalidatedEvent(
    val json: JSONObject,
) : ToncenterEvent("trace_invalidated", "") {

    val traceExternalHashNorm: String?
        get() = json.optString("trace_external_hash_norm").takeIf { it.isNotEmpty() }
}
