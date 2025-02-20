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
        get() = url.host ?: "unknown"

    constructor(json: JSONObject) : this(
        url = Uri.parse(json.getString("url").removeSuffix("/")),
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