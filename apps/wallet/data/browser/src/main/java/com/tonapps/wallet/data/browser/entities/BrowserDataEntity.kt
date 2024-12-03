package com.tonapps.wallet.data.browser.entities

import android.os.Parcelable
import android.util.Log
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class BrowserDataEntity(
    val apps: List<BrowserAppEntity>,
    val categories: List<BrowserCategoryEntity>
): Parcelable {

    constructor(json: JSONObject) : this(
        apps = BrowserAppEntity.parse(json.getJSONArray("apps")),
        categories = BrowserCategoryEntity.parse(json.getJSONArray("categories"))
    )
}