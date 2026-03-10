package com.tonapps.wallet.api.entity

import org.json.JSONObject

data class OnRampMerchantEntity(
    val merchant: String,
    val amount: Double,
    val widgetUrl: String,
    val minAmount: Double,
) {

    data class Data(
        val items: List<OnRampMerchantEntity> = emptyList(),
        val suggested: List<OnRampMerchantEntity> = emptyList()
    ) {

        val isEmpty: Boolean
            get() = items.isEmpty() && suggested.isEmpty()

        val size: Int
            get() = items.size + suggested.size
    }

    constructor(json: JSONObject) : this(
        merchant = json.getString("merchant"),
        amount = json.getDouble("amount"),
        widgetUrl = json.getString("widget_url"),
        minAmount = json.optDouble("min_amount", 0.0)
    )
}