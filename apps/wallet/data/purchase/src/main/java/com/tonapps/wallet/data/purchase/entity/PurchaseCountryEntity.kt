package com.tonapps.wallet.data.purchase.entity

import android.os.Parcelable
import com.tonapps.extensions.toStringList
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject

@Parcelize
data class PurchaseCountryEntity(
    val countryCode: String,
    val currency: String,
    val methods: List<String>
): Parcelable {

    companion object {
        fun parse(array: JSONArray): List<PurchaseCountryEntity> {
            val list = mutableListOf<PurchaseCountryEntity>()
            for (i in 0 until array.length()) {
                list.add(PurchaseCountryEntity(array.getJSONObject(i)))
            }
            return list
        }
    }

    constructor(json: JSONObject) : this(
        countryCode = json.getString("countryCode"),
        currency = json.getString("currency"),
        methods = json.getJSONArray("methods").toStringList()
    )
}