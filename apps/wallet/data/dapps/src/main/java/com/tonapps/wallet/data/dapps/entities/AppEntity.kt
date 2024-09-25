package com.tonapps.wallet.data.dapps.entities

import android.os.Parcelable
import androidx.core.net.toUri
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class AppEntity(
    val url: String,
    val name: String,
    val iconUrl: String,
    val empty: Boolean
): Parcelable {

    val host: String
        get() = url.toUri().host ?: "unknown"

    constructor(json: JSONObject) : this(
        url = json.getString("url"),
        name = json.getString("name"),
        iconUrl = json.getString("iconUrl"),
        empty = false,
    )

    constructor(value: String) : this(JSONObject(value))
}