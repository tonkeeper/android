package com.tonapps.wallet.data.purchase.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class PurchaseDataEntity(
    val layoutByCountry: List<PurchaseCountryEntity>,
    val buy: List<PurchaseCategoryEntity>,
    val sell: List<PurchaseCategoryEntity>,
): Parcelable {

    constructor(json: JSONObject) : this(
        layoutByCountry = PurchaseCountryEntity.parse(json.getJSONArray("layoutByCountry")),
        buy = PurchaseCategoryEntity.parse(json.getJSONArray("buy")),
        sell = PurchaseCategoryEntity.parse(json.getJSONArray("sell"))
    )

    fun getCountry(country: String) = layoutByCountry.find {
        it.countryCode == country
    } ?: layoutByCountry.first()


    fun getMethod(id: String): PurchaseMethodEntity? {
        val methods = (buy + sell).map { it.items }.flatten()
        return methods.find { it.id == id }
    }
}