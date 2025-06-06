package com.tonapps.wallet.api.entity

import com.tonapps.icu.Coins
import org.json.JSONObject

data class OnRampArgsEntity(
    val from: String,
    val to: String,
    val network: String,
    val wallet: String,
    val purchaseType: String,
    val amount: Coins,
    val country: String,
    val paymentMethod: String?
) {

    fun toJSON() = JSONObject().apply {
        put("from", from)
        put("to", to)
        put("network", network)
        put("wallet", wallet)
        put("purchase_type", purchaseType)
        put("amount", amount.value.toPlainString())
        put("country", country)
        paymentMethod?.let {
            put("payment_method", it)
        }
    }
}
