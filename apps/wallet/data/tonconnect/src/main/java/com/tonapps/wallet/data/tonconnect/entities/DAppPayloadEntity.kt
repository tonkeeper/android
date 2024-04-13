package com.tonapps.wallet.data.tonconnect.entities

import org.json.JSONObject

data class DAppPayloadEntity(
    val manifestUrl: String,
    val items: List<DAppItemEntity>
) {

    constructor(json: JSONObject) : this(
        manifestUrl = json.getString("manifestUrl"),
        items = DAppItemEntity.parse(json.optJSONArray("items"))
    )
}