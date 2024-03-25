package com.tonapps.tonkeeper.core.fiat.models

import com.tonapps.tonkeeper.extensions.getStringArray
import com.tonapps.tonkeeper.extensions.toJSONArray
import org.json.JSONArray
import org.json.JSONObject

data class FiatLayout(
    val countryCode: String?,
    val currency: String?,
    val methods: List<String>
): BaseFiat() {
    companion object {
        fun parse(array: JSONArray): List<FiatLayout> {
            val list = mutableListOf<FiatLayout>()
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                list.add(FiatLayout(json))
            }
            return list
        }
    }

    constructor(json: JSONObject) : this(
        countryCode = json.optString("countryCode"),
        currency = json.optString("currency"),
        methods = json.getStringArray("methods")
    )

    override fun toJSON(): JSONObject {
        return JSONObject().apply {
            put("country_code", countryCode)
            put("currency", currency)
            put("methods", methods.toJSONArray())
        }
    }
}