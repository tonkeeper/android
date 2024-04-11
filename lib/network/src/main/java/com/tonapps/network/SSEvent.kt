package com.tonapps.network

import org.json.JSONObject

data class SSEvent(
    val id: String?,
    val type: String?,
    val data: String
) {
    val json = JSONObject(data)
}