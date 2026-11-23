package com.tonapps.wallet.api.core

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class HermesToken(val value: String, val expiresAtMs: Long?) {

    fun needsRenewal(nowMs: Long): Boolean {
        return expiresAtMs == null || expiresAtMs - nowMs <= HermesAPI.TOKEN_RENEW_LEAD_MS
    }

    fun renewDelayMs(nowMs: Long): Long {
        val untilLeadMs = expiresAtMs?.let { it - nowMs - HermesAPI.TOKEN_RENEW_LEAD_MS } ?: 0L
        return untilLeadMs.coerceAtLeast(HermesAPI.TOKEN_RENEW_MIN_DELAY_MS)
    }
}

class HermesAPI(
    private val url: String,
    okHttpClient: OkHttpClient,
    private val tokenProvider: () -> String?,
    private val tokenRefresher: (String) -> String?,
    private val tokenExpiry: (String) -> Long?,
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

    fun loginToken(): HermesToken? {
        val value = tokenProvider() ?: return null
        val current = HermesToken(value, tokenExpiry(value))
        val nowMs = System.currentTimeMillis()
        if (!current.needsRenewal(nowMs)) {
            return current
        }
        val renewed = tokenRefresher(current.value)
        if (renewed != null) {
            return HermesToken(renewed, tokenExpiry(renewed))
        }
        val expiresAtMs = current.expiresAtMs
        if (expiresAtMs == null || expiresAtMs > nowMs) {
            return current
        }
        return null
    }

    fun refreshAccessToken(expiredToken: String): String? {
        return tokenRefresher(expiredToken)
    }

    internal companion object {
        const val PING_INTERVAL_SECONDS = 30L
        const val TOKEN_RENEW_LEAD_MS = 120_000L
        const val TOKEN_RENEW_MIN_DELAY_MS = 30_000L
    }
}
