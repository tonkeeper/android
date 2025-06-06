package com.tonapps.wallet.api.entity

import org.json.JSONObject

data class OnRampMerchantEntity(
    val merchant: String,
    val amount: Double,
    val widgetUrl: String
) {

    constructor(json: JSONObject) : this(
        merchant = json.getString("merchant"),
        amount = json.getDouble("amount"),
        widgetUrl = json.getString("widget_url")
    )
}