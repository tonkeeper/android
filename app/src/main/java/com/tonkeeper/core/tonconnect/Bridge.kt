package com.tonkeeper.core.tonconnect

import com.tonkeeper.api.withRetry
import com.tonkeeper.core.tonconnect.models.TCApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.ton.crypto.base64
import org.ton.crypto.hex
import core.network.Network

internal class Bridge {

    companion object {
        const val DEFAULT_URL = "https://bridge.tonapi.io/bridge"
        const val DEFAULT_TTL = 300L
    }

    suspend fun sendEvent(body: JSONObject, app: TCApp) {
        sendEvent(body.toString(), app)
    }

    suspend fun sendEvent(
        body: String,
        app: TCApp
    ) = withContext(Dispatchers.IO) {
        val url = "$DEFAULT_URL/message?client_id=${hex(app.publicKey)}&to=${app.clientId}&ttl=$DEFAULT_TTL"

        val encoded = app.encrypt(body)

        val mimeType = "text/plain".toMediaType()
        val request = Network.newRequest(url)
            .post(base64(encoded).toRequestBody(mimeType))
            .build()

        val response = withRetry { Network.request(request) } ?: throw Exception("Error sending event: null response")
        if (!response.isSuccessful) {
            throw Exception("Error sending event: ${response.code}")
        }
    }

}