package com.tonkeeper.ton.console.model

import com.tonkeeper.ton.extensions.getStringsList
import org.json.JSONObject

data class AccountModel(
    val address: String,
    val balance: Long,
    val lastActivity: String,
    val status: String,
    val interfaces: List<String>,
    val name: String?,
    val isScam: Boolean,
    val icon: String?,
    val memoRequired: Boolean,
    val getMethods: List<String>,
    val isSuspended: Boolean
) {

    constructor(json: JSONObject) : this(
        json.getString("address"),
        json.getLong("balance"),
        json.getString("last_activity"),
        json.getString("status"),
        json.getStringsList("interfaces"),
        json.optString("name"),
        json.optBoolean("is_scam"),
        json.optString("icon"),
        json.optBoolean("memo_required"),
        json.getStringsList("get_methods"),
        json.optBoolean("is_suspended")
    )
}