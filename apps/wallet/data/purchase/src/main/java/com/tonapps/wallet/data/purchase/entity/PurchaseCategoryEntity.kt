package com.tonapps.wallet.data.purchase.entity

import android.os.Parcelable
import io.tonendpointapi.models.FiatCategory
import kotlinx.parcelize.Parcelize

@Parcelize
data class PurchaseCategoryEntity(
    val type: String,
    val title: String,
    val subtitle: String,
    val assets: List<String>,
    val items: List<PurchaseMethodEntity>,
): Parcelable {

    constructor(category: FiatCategory) : this(
        type = category.type,
        title = category.title,
        subtitle = category.subtitle,
        assets = category.assets.orEmpty(),
        items = category.items.orEmpty().map(::PurchaseMethodEntity)
    )
}