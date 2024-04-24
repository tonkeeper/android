package com.tonapps.wallet.data.browser.entities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject

@Parcelize
data class BrowserCategoryEntity(
    val id: String,
    val title: String,
    val apps: List<BrowserAppEntity>
): Parcelable {

    constructor(json: JSONObject) : this(
        id = json.getString("id"),
        title = json.getString("title"),
        apps = BrowserAppEntity.parse(json.getJSONArray("apps"))
    )

    companion object {

        fun parse(array: JSONArray): List<BrowserCategoryEntity> {
            return (0 until array.length()).map { BrowserCategoryEntity(array.getJSONObject(it)) }
        }
    }
}