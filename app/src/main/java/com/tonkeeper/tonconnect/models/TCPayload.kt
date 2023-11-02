package com.tonkeeper.tonconnect.models

import org.json.JSONObject

data class TCPayload(
    val manifestUrl: String,
    val items: List<TCItem>
) {

    constructor(json: JSONObject) : this(
        manifestUrl = json.getString("manifestUrl"),
        items = TCItem.parse(json.optJSONArray("items"))
    )

    constructor(data: String) : this(
        JSONObject(data)
    )
}