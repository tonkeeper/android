package com.tonkeeper.core.tonconnect.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONObject

@Parcelize
data class TCPayload(
    val manifestUrl: String,
    val items: List<TCItem>
) : Parcelable {

    constructor(json: JSONObject) : this(
        manifestUrl = json.getString("manifestUrl"),
        items = TCItem.parse(json.optJSONArray("items"))
    )

    constructor(data: String) : this(
        JSONObject(data)
    )
}