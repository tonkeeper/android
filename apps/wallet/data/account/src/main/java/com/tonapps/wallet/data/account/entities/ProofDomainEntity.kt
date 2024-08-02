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

    fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("lengthBytes", lengthBytes)
        json.put("value", value)
        return json
    }
}