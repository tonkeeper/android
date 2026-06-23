package com.tonapps.wallet.api.entity.toncenter

import org.json.JSONArray
import org.json.JSONObject

data class ToncenterSSERequest(
    val addresses: List<String>,
    val types: List<String>,
    val minFinality: String,
    val traceExternalHashNorms: List<String>? = null,
    val includeAddressBook: Boolean? = null,
    val includeMetadata: Boolean? = null,
    val actionTypes: List<String>? = null,
    val supportedActionTypes: List<String>? = null,
) {

    fun toJSON(): JSONObject = JSONObject().apply {
        put("addresses", JSONArray(addresses))
        put("types", JSONArray(types))
        put("min_finality", minFinality)
        traceExternalHashNorms?.let { put("trace_external_hash_norms", JSONArray(it)) }
        includeAddressBook?.let { put("include_address_book", it) }
        includeMetadata?.let { put("include_metadata", it) }
        actionTypes?.let { put("action_types", JSONArray(it)) }
        supportedActionTypes?.let { put("supported_action_types", JSONArray(it)) }
    }
}
