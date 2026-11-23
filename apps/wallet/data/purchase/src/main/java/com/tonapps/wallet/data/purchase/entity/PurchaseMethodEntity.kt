package com.tonapps.wallet.data.purchase.entity

import android.os.Parcelable
import io.tonendpointapi.models.FiatCategoryItemsInner
import kotlinx.parcelize.Parcelize

@Parcelize
data class PurchaseMethodEntity(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val iconUrl: String,
    val infoButtons: List<Button>,
    val actionButton: Button,
    val successUrlPattern: SuccessUrlPattern?,
): Parcelable {

    val useCustomTabs: Boolean
        get() = id == "mercuryo"

    @Parcelize
    data class Button(
        val title: String,
        val url: String
    ): Parcelable

    @Parcelize
    data class SuccessUrlPattern(
        val pattern: String,
        val purchaseIdIndex: Int
    ): Parcelable

    constructor(item: FiatCategoryItemsInner) : this(
        id = item.id,
        title = item.title,
        subtitle = item.subtitle,
        description = item.description,
        iconUrl = item.iconUrl,
        successUrlPattern = item.successUrlPattern?.let { SuccessUrlPattern(it.pattern, it.purchaseIdIndex ?: -1) },
        infoButtons = item.infoButtons.orEmpty().map { Button(it.title, it.url) },
        actionButton = Button(item.actionButton.title, item.actionButton.url),
    )
}