package com.tonapps.wallet.data.browser.entities

import android.os.Parcelable
import io.tonendpointapi.models.GetPopularApps200ResponseData
import kotlinx.parcelize.Parcelize

@Parcelize
data class BrowserDataEntity(
    val apps: List<BrowserAppEntity>,
    val categories: List<BrowserCategoryEntity>
): Parcelable {

    constructor(data: GetPopularApps200ResponseData) : this(
        apps = data.apps.map(::BrowserAppEntity),
        categories = data.categories.map(::BrowserCategoryEntity)
    )
}