package com.tonapps.tonkeeper.core.tonconnect.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject

@Parcelize
data class TCItem(
    val name: String,
    val payload: String?
) : Parcelable {
    companion object {
        const val TON_ADDR = "ton_addr"
        const val TON_PROOF = "ton_proof"

        fun parse(array: JSONArray?): List<TCItem> {
            if (array == null || array.length() == 0) return emptyList()
            val list = mutableListOf<TCItem>()
            for (i in 0 until array.length()) {
                list.add(TCItem(array.getJSONObject(i)))
            }
            return list
        }
    }

    constructor(json: JSONObject) : this(
        name = json.getString("name"),
        payload = json.optString("payload")
    )
}