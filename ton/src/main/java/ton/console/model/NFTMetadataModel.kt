package com.tonkeeper.ton.console.model

import org.json.JSONObject

data class NFTMetadataModel(
    val name: String?
) {

    constructor(json: JSONObject) : this(
        name = json.optString("name"),
    )
}