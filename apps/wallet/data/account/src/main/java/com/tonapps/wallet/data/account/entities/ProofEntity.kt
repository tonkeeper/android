package com.tonapps.wallet.data.account.entities

import kotlinx.serialization.Serializable
import org.json.JSONObject

@Serializable
data class ProofEntity(
    val timestamp: Long,
    val domain: ProofDomainEntity,
    val payload: String?,
    val signature: String,
    val stateInit: String? = null
) {

    fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("timestamp", timestamp)
        json.put("domain", domain.toJSON())
        json.put("payload", payload)
        json.put("signature", signature)
        json.put("stateInit", stateInit)
        return json
    }

}