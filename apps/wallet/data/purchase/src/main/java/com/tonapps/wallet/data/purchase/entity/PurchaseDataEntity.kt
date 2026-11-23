package com.tonapps.wallet.data.purchase.entity

import android.os.Parcelable
import io.tonendpointapi.models.GetFiatMethods200ResponseData
import kotlinx.parcelize.Parcelize

@Parcelize
data class PurchaseDataEntity(
    val layoutByCountry: List<PurchaseCountryEntity>,
    val buy: List<PurchaseCategoryEntity>,
    val sell: List<PurchaseCategoryEntity>,
): Parcelable {

    constructor(data: GetFiatMethods200ResponseData) : this(
        layoutByCountry = data.layoutByCountry.map { PurchaseCountryEntity(it.countryCode, it.currency, it.methods) },
        buy = data.buy.map(::PurchaseCategoryEntity),
        sell = data.sell.map(::PurchaseCategoryEntity)
    )

    fun getCountry(country: String) = layoutByCountry.find {
        it.countryCode == country
    } ?: layoutByCountry.first()


    fun getMethod(id: String): PurchaseMethodEntity? {
        val methods = (buy + sell).map { it.items }.flatten()
        return methods.find { it.id == id }
    }
}