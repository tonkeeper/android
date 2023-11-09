package com.tonkeeper.core.tonconnect

import android.util.Log
import com.google.crypto.tink.subtle.Hex
import com.tonkeeper.core.tonconnect.models.TCKeyPair
import core.extensions.toBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import ton.console.Network

internal class Bridge {

    companion object {
        const val DEFAULT_URL = "https://bridge.tonapi.io/bridge"
        const val DEFAULT_TTL = 300L
    }

    suspend fun sendEvent(
        data: JSONObject,
        clientSessionId: String,
        keyPair: TCKeyPair
    ) {
        sendEvent(data.toString(), clientSessionId, keyPair)
    }

    suspend fun sendEvent(
        data: String,
        clientSessionId: String,
        keyPair: TCKeyPair
    ) = withContext(Dispatchers.IO) {
        val sessionCrypto = SessionCrypto(keyPair)
        val url = "$DEFAULT_URL/message?client_id=${sessionCrypto.sessionId}&to=$clientSessionId&ttl=$DEFAULT_TTL"

        val encoded = sessionCrypto.encrypt(
            data,
            Hex.decode(clientSessionId)
        )

        val body = encoded.toBase64()

        val mimeType = "text/plain".toMediaType()
        val request = Network.newRequest(url)
            .post(body.toRequestBody(mimeType))
            .build()

        val res = Network.request(request)
        Log.d("TonConnectLog", "res: $res")

    }

}