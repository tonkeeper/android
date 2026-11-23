package com.tonapps.wallet.data.browser.entities

import android.os.Parcelable
import io.tonendpointapi.models.PopularCategory
import kotlinx.parcelize.Parcelize

@Parcelize
data class BrowserCategoryEntity(
    val id: String,
    val title: String,
    val apps: List<BrowserAppEntity>
): Parcelable {

    constructor(category: PopularCategory) : this(
        id = category.id,
        title = category.title,
        apps = category.apps.map(::BrowserAppEntity)
    )

    companion object {

        val CHAIN_INDEPENDENT_IDS = setOf("digital_nomads", "ads")
    }
}