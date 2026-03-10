package com.tonapps.wallet.api.entity

import com.tonapps.icu.Coins
import org.json.JSONObject

data class OnRampArgsEntity(
    val from: String,
    val to: String,
    val fromNetwork: String?,
    val toNetwork: String?,
    val wallet: String,
    val purchaseType: String,
    val amount: Coins,
    val paymentMethod: String?
) {

    val isSwap: Boolean
        get() = purchaseType == "swap"

    val isSell: Boolean
        get() = purchaseType == "sell"

    val withoutPaymentMethod: Boolean
        get() = isSwap || isSell

    fun toJSON() = JSONObject().apply {
        put("from", from)
        put("to", to)
        fromNetwork?.let {
            put("from_network", it)
        }
        toNetwork?.let {
            put("to_network", it)
        }
        put("wallet", wallet)
        put("purchase_type", purchaseType)
        put("amount", amount.value.toPlainString())
        paymentMethod?.let {
            put("payment_method", it)
        }
    }
}
