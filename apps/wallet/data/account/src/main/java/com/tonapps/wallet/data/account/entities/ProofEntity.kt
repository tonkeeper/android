package com.tonapps.wallet.data.account.entities

import org.json.JSONObject

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