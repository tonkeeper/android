package com.tonapps.wallet.data.browser.entities

import android.graphics.Color
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import androidx.core.net.toUri
import com.tonapps.extensions.toUriOrNull
import kotlinx.parcelize.IgnoredOnParcel
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
    val button: Button? = null,
): Parcelable {

    @IgnoredOnParcel
    val useCustomTabs: Boolean
        get() = url.host?.endsWith("mercuryo.io") == true

    @IgnoredOnParcel
    val useTG: Boolean
        get() = url.host == "t.me"

    @IgnoredOnParcel
    val host: String?
        get() = url.host

    @Parcelize
    data class Button(
        val type: String,
        val payload: String,
        val title: String
    ): Parcelable {

        constructor(json: JSONObject) : this(
            type = json.getString("type"),
            payload = json.getString("payload"),
            title = json.getString("title")
        )
    }

    constructor(json: JSONObject) : this(
        name = json.getString("name"),
        description = json.getString("description"),
        icon = Uri.parse(json.getString("icon")),
        poster = json.optString("poster")?.let { Uri.parse(it) },
        url = parseUrl(json.optString("url")),
        textColor = Color.parseColor(json.optString("textColor", "#ffffff")),
        button = json.optJSONObject("button")?.let { Button(it) }
    )

    companion object {

        fun parse(array: JSONArray): List<BrowserAppEntity> {
            return (0 until array.length()).map { BrowserAppEntity(array.getJSONObject(it)) }
        }

        private fun parseUrl(value: String?): Uri {
            if (value.isNullOrBlank()) {
                return Uri.EMPTY
            }
            if (value.startsWith("http://")) {
                return parseUrl(value.replace("http://", "https://"))
            }
            val uri = value.toUriOrNull() ?: return Uri.EMPTY
            if (uri.scheme.isNullOrBlank()) {
                return parseUrl("https://$value")
            }
            return uri
        }
    }
}
