package com.tonapps.tonkeeper.api.internal

import androidx.collection.ArrayMap
import org.json.JSONObject

data class TonkeeperKeys(
    val variables: ArrayMap<String, String>,
    val flags: ArrayMap<String, Boolean>
) {

    companion object {

        private fun parseVariables(json: JSONObject): ArrayMap<String, String> {
            val variables = ArrayMap<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.optString(key) ?: continue
                variables[key] = value
            }
            return variables
        }

        private fun parseFlags(json: JSONObject): ArrayMap<String, Boolean> {
            val flags = ArrayMap<String, Boolean>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                flags[key] = json.getBoolean(key)
            }
            return flags
        }
    }

    constructor(json: JSONObject) : this(
        variables = parseVariables(json),
        flags = parseFlags(json.getJSONObject("flags"))
    )

    constructor(data: String) : this(
        JSONObject(data)
    )
}