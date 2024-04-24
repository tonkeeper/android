package com.tonapps.wallet.data.browser.entities

import android.graphics.Color
import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject

@Parcelize
data class BrowserAppEntity(
    val name: String,
    val description: String,
    val icon: Uri,
    val poster: Uri?,
    val url: Uri,
    val textColor: Int,
): Parcelable {

    constructor(json: JSONObject) : this(
        name = json.getString("name"),
        description = json.getString("description"),
        icon = Uri.parse(json.getString("icon")),
        poster = json.optString("poster")?.let { Uri.parse(it) },
        url = Uri.parse(json.getString("url")),
        textColor = Color.parseColor(json.optString("textColor", "#ffffff"))
    )

    companion object {

        fun parse(array: JSONArray): List<BrowserAppEntity> {
            return (0 until array.length()).map { BrowserAppEntity(array.getJSONObject(it)) }
        }
    }
}
