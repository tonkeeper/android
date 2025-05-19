package com.tonapps.wallet.data.dapps.entities

import android.net.Uri
import android.os.Parcelable
import androidx.core.net.toUri
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class AppEntity(
    val url: Uri,
    val name: String,
    val iconUrl: String,
    val empty: Boolean
): Parcelable {

    @IgnoredOnParcel
    val id: String
        get() = url.toString()

    @IgnoredOnParcel
    val host: String
        get() = url.host ?: id

    @IgnoredOnParcel
    val isBadIcon: Boolean by lazy {
        iconUrl.isBlank() || iconUrl.endsWith("favicon.ico")
    }

    @IgnoredOnParcel
    val iconByFavicon: String by lazy {
        "https://www.google.com/s2/favicons?sz=256&domain=$host"
    }

    constructor(json: JSONObject) : this(
        url = json.getString("url").removeSuffix("/").toUri(),
        name = json.getString("name").ifBlank {
            throw IllegalArgumentException("name is empty")
        },
        iconUrl = json.getString("iconUrl").ifBlank {
            throw IllegalArgumentException("iconUrl is empty")
        },
        empty = false,
    )

    constructor(value: String) : this(JSONObject(value))
}