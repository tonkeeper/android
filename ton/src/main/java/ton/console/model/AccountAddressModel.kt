package com.tonkeeper.ton.console.model

import com.tonkeeper.ton.TonAddress
import org.json.JSONArray
import org.json.JSONObject

data class AccountAddressModel(
    val address: String,
    val name: String?,
    val scam: Boolean,
    val icon: String?
) {

    companion object {

        fun parse(json: JSONObject?): AccountAddressModel? {
            if (json == null) return null
            return AccountAddressModel(json)
        }

        fun parse(array: JSONArray?): List<AccountAddressModel> {
            if (array == null) return emptyList()
            return (0 until array.length()).map { index ->
                AccountAddressModel(array.getJSONObject(index))
            }
        }
    }

    val tonAddress: TonAddress by lazy {
        TonAddress(address)
    }

    constructor(json: JSONObject) : this(
        address = json.getString("address"),
        name = json.optString("name"),
        scam = json.optBoolean("isScam"),
        icon = json.optString("icon")
    )
}