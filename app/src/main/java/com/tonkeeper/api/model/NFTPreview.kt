package com.tonkeeper.api.model

import org.json.JSONArray
import org.json.JSONObject

data class NFTPreview(
    val resolution: String,
    val url: String
) {

    companion object {

        fun parse(array: JSONArray): List<NFTPreview> {
            val list = mutableListOf<NFTPreview>()
            for (i in 0 until array.length()) {
                list.add(NFTPreview(array.getJSONObject(i)))
            }
            return list
        }
    }

    constructor(json: JSONObject) : this(
        resolution = json.getString("resolution"),
        url = json.getString("url")
    )
}