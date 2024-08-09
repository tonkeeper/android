package com.tonapps.wallet.data.account.entities

import kotlinx.serialization.Serializable
import org.json.JSONObject

@Serializable
data class ProofDomainEntity(
    val lengthBytes: Int,
    val value: String
) {

    constructor(value: String) : this(
        lengthBytes = value.toByteArray().size,
        value = value
    )

    fun toJSON(camelCase: Boolean): JSONObject {
        val json = JSONObject()
        if (camelCase) {
            json.put("lengthBytes", lengthBytes)

        } else {
            json.put("length_bytes", lengthBytes)
        }
        json.put("value", value)
        return json
    }
}