package com.tonkeeper.api.model

import org.json.JSONObject

data class AccountModel(
    val balance: Long
) {

    constructor(json: JSONObject) : this(
        json.getLong("balance")
    )
}