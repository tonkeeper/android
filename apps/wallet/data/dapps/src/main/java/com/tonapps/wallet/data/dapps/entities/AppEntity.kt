package com.tonapps.wallet.data.dapps.entities

import android.net.Uri
import android.os.Parcelable
import androidx.core.net.toUri
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class AppEntity(
    val url: Uri,
    val name: String,
    val iconUrl: String,
    val empty: Boolean
): Parcelable {

    val host: String
        get() = url.host ?: "unknown"

    constructor(json: JSONObject) : this(
        url = Uri.parse(json.getString("url").removeSuffix("/")),
        name = json.getString("name"),
        iconUrl = json.getString("iconUrl"),
        empty = false,
    )

    constructor(value: String) : this(JSONObject(value))
}