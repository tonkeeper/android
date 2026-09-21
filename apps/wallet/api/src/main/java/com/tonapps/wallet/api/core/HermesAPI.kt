package com.tonapps.wallet.api.core

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class HermesAPI(
    private val url: String,
    okHttpClient: OkHttpClient,
    private val tokenProvider: () -> String?,
) {

    // Protocol pings carry liveness; a push stream must never expire on a read timeout.
    private val wsClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    fun connect(listener: WebSocketListener): WebSocket {
        val request = Request.Builder()
            .url(url)
            .build()
        return wsClient.newWebSocket(request, listener)
    }

    fun accessToken(): String? {
        return tokenProvider()
    }

    private companion object {
        const val PING_INTERVAL_SECONDS = 30L
    }
}
