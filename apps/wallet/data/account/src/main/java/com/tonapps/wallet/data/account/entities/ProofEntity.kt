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

    fun toJSON(camelCase: Boolean): JSONObject {
        val json = JSONObject()
        json.put("timestamp", timestamp)
        json.put("domain", domain.toJSON(camelCase))
        json.put("payload", payload)
        json.put("signature", signature)
        if (camelCase) {
            json.put("stateInit", stateInit)
        } else {
            json.put("state_init", stateInit)
        }
        return json
    }

    fun string(camelCase: Boolean) = toJSON(camelCase).toString()
}