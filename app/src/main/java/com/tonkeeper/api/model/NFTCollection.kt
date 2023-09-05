package com.tonkeeper.api.model

import org.json.JSONObject

data class NFTCollection(
    val name: String,
    val description: String?,
) {

    companion object {
        fun parse(json: JSONObject?): NFTCollection {
            if (json == null) {
                return NFTCollection(
                    name = "",
                    description = null
                )
            }
            return NFTCollection(json)
        }
    }

    constructor(json: JSONObject) : this(
        name = json.getString("name"),
        description = json.optString("description"),
    )
}