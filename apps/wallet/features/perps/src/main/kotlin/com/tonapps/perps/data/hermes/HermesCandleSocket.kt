package com.tonapps.perps.data.hermes

import com.tonapps.log.L
import com.tonapps.wallet.api.core.HermesAPI
import io.kandelabrapi.models.GetCandlesResponse
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.atomic.AtomicLong

sealed interface HermesCandleEvent {
    data object Opened : HermesCandleEvent
    data class Candles(val response: GetCandlesResponse) : HermesCandleEvent

    /** The feed will never deliver on this connection — reconnecting cannot fix it. */
    data object FeedRejected : HermesCandleEvent
    data object Terminated : HermesCandleEvent
}

class HermesCandleSocket(private val hermes: HermesAPI) {

    // DROP_OLDEST is safe: pushes are full-bucket re-sends and can never overtake a control event.
    private val inbound = Channel<HermesCandleEvent>(INBOUND_CAPACITY, BufferOverflow.DROP_OLDEST)
    private val nextRequestId = AtomicLong(0L)

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var subscribedFeedId: String? = null

    val events: ReceiveChannel<HermesCandleEvent>
        get() = inbound

    private val listener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            inbound.trySend(HermesCandleEvent.Opened)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            decode(text)?.let { inbound.trySend(it) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(CLOSE_NORMAL, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            inbound.trySend(HermesCandleEvent.Terminated)
            inbound.close()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            L.e(t)
            inbound.trySend(HermesCandleEvent.Terminated)
            inbound.close()
        }
    }

    fun connect() {
        socket = hermes.connect(listener)
    }

    fun login(token: String) {
        request(CHANNEL_LOGIN, NAME_LOGIN, buildJsonObject { put("token", token) })
    }

    fun subscribe(feedId: String) {
        subscribedFeedId = feedId
        request(CHANNEL_SUBSCRIBE, NAME_SUBSCRIBE, buildJsonObject { put("feedId", feedId) })
    }

    suspend fun keepAlive() {
        while (true) {
            delay(PING_INTERVAL_MS)
            request(CHANNEL_PING, NAME_PING)
        }
    }

    fun close() {
        subscribedFeedId?.let { feedId ->
            request(CHANNEL_UNSUBSCRIBE, NAME_UNSUBSCRIBE, buildJsonObject { put("feedId", feedId) })
        }
        subscribedFeedId = null
        socket?.close(CLOSE_NORMAL, null)
        socket = null
        inbound.close()
    }

    private fun request(channel: String, name: String, payload: JsonObject? = null) {
        val socket = socket ?: return
        socket.send(hermesFrame(channel, name, nextRequestId.incrementAndGet(), payload))
    }

    private fun decode(text: String): HermesCandleEvent? {
        try {
            val envelope = hermesJson.decodeFromString<InboundEnvelope>(text)
            val message = envelope.message ?: return null
            if (envelope.channel == CHANNEL_CANDLES && message.name == NAME_CANDLES_PUSH) {
                val payload = message.payload ?: return null
                val push = hermesJson.decodeFromJsonElement<CandlesPush>(payload)
                return HermesCandleEvent.Candles(push.data)
            }
            if (message.name == NAME_ERROR_RESPONSE || message.name == NAME_ERROR_MESSAGE) {
                val error = message.payload?.let { hermesJson.decodeFromJsonElement<ErrorPayload>(it) }
                L.e("hermes ${envelope.channel} error: ${error?.code} ${error?.description}")
                if (envelope.channel != CHANNEL_SUBSCRIBE) {
                    return null
                }
                val code = error?.code.orEmpty()
                if (code == HERMES_ERROR_ALREADY_SUBSCRIBED) {
                    return null
                }
                if (code in PERMANENT_FEED_ERRORS) {
                    return HermesCandleEvent.FeedRejected
                }
                return HermesCandleEvent.Terminated
            }
            return null
        } catch (e: SerializationException) {
            L.e(e)
            return null
        }
    }

    @Serializable
    private data class CandlesPush(
        val data: GetCandlesResponse,
    )

    private companion object {
        const val CHANNEL_PING = "ping"
        const val CHANNEL_LOGIN = "login"
        const val CHANNEL_SUBSCRIBE = "subscribeCandleFeed"
        const val CHANNEL_UNSUBSCRIBE = "unsubscribeCandleFeed"
        const val CHANNEL_CANDLES = "candles"

        const val NAME_PING = "ping"
        const val NAME_LOGIN = "loginRequest"
        const val NAME_SUBSCRIBE = "subscribeCandleFeedRequest"
        const val NAME_UNSUBSCRIBE = "unsubscribeCandleFeedRequest"
        const val NAME_CANDLES_PUSH = "candlesFeedMessage"
        const val NAME_ERROR_RESPONSE = "errorResponse"
        const val NAME_ERROR_MESSAGE = "errorMessage"

        val PERMANENT_FEED_ERRORS = setOf("invalid.request", "not.found", "unauthorized")

        const val INBOUND_CAPACITY = 256

        // Hermes drops sessions idle for 300 s.
        const val PING_INTERVAL_MS = 120_000L
        const val CLOSE_NORMAL = 1000
    }
}
