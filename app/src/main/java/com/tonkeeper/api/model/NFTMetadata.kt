package com.tonkeeper.api.model

import org.json.JSONObject

data class NFTMetadata(
    val name: String?
) {

    constructor(json: JSONObject) : this(
        name = json.optString("name"),
    )
}