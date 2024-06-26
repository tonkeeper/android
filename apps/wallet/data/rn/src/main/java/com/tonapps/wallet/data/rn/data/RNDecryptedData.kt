package com.tonapps.wallet.data.rn.data

import org.json.JSONObject

data class RNDecryptedData(
    val identifier: String,
    val mnemonic: String
) {

    companion object {

        fun toJSON(list: List<RNDecryptedData>): JSONObject {
            val json = JSONObject()
            for (m in list) {
                json.put(m.identifier, m.toJSON())
            }
            return json
        }

        fun string(list: List<RNDecryptedData>): String {
            return toJSON(list).toString()
        }
    }

    val string: String
        get() = toJSON().toString()

    constructor(json: JSONObject) : this(
        identifier = json.getString("identifier"),
        mnemonic = json.getString("mnemonic")
    )

    fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("identifier", identifier)
        json.put("mnemonic", mnemonic)
        return json
    }
}