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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

sealed interface HermesCandleEvent {
    data object Opened : HermesCandleEvent
    data class Candles(val feedId: String?, val response: GetCandlesResponse) : HermesCandleEvent
    data object LoggedIn : HermesCandleEvent
    data object LoginRejected : HermesCandleEvent
    data object LoginFailed : HermesCandleEvent
    data class FeedError(val feedId: String?, val permanent: Boolean) : HermesCandleEvent
    data object Terminated : HermesCandleEvent
}

class HermesCandleSocket(private val hermes: HermesAPI) {

    // DROP_OLDEST is safe: pushes are full-bucket re-sends and can never overtake a control event.
    private val inbound = Channel<HermesCandleEvent>(INBOUND_CAPACITY, BufferOverflow.DROP_OLDEST)
    private val nextRequestId = AtomicLong(0L)

    @Volatile
    private var socket: WebSocket? = null

    private val subscriptions = ConcurrentHashMap<Long, String>()

    @Volatile
    private var loginRequestId: Long = 0L

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
        loginRequestId = nextRequestId.incrementAndGet()
        request(CHANNEL_LOGIN, NAME_LOGIN, buildJsonObject { put("token", token) }, loginRequestId)
    }

    fun subscribe(feedId: String) {
        val reqid = nextRequestId.incrementAndGet()
        subscriptions[reqid] = feedId
        request(CHANNEL_SUBSCRIBE, NAME_SUBSCRIBE, buildJsonObject { put("feedId", feedId) }, reqid)
    }

    suspend fun keepAlive() {
        while (true) {
            delay(PING_INTERVAL_MS)
            request(CHANNEL_PING, NAME_PING)
        }
    }

    fun close() {
        subscriptions.values.distinct().forEach { feedId ->
            request(CHANNEL_UNSUBSCRIBE, NAME_UNSUBSCRIBE, buildJsonObject { put("feedId", feedId) })
        }
        subscriptions.clear()
        socket?.close(CLOSE_NORMAL, null)
        socket = null
        inbound.close()
    }

    private fun request(
        channel: String,
        name: String,
        payload: JsonObject? = null,
        reqid: Long = nextRequestId.incrementAndGet(),
    ) {
        val socket = socket ?: return
        socket.send(hermesFrame(channel, name, reqid, payload))
    }

    private fun decode(text: String): HermesCandleEvent? {
        try {
            val envelope = hermesJson.decodeFromString<InboundEnvelope>(text)
            val message = envelope.message ?: return null
            val loginReply = message.loginReply(loginRequestId)
            if (loginReply != null) {
                if (loginReply != HermesLoginReply.ACCEPTED) {
                    val error = message.payload?.let { hermesJson.decodeFromJsonElement<ErrorPayload>(it) }
                    L.e("hermes ${envelope.channel} error: ${error?.code} ${error?.description}")
                }
                return when (loginReply) {
                    HermesLoginReply.ACCEPTED -> HermesCandleEvent.LoggedIn
                    HermesLoginReply.REJECTED -> HermesCandleEvent.LoginRejected
                    HermesLoginReply.FAILED -> HermesCandleEvent.LoginFailed
                }
            }
            if (envelope.channel == CHANNEL_CANDLES && message.name == NAME_CANDLES_PUSH) {
                val payload = message.payload ?: return null
                val push = hermesJson.decodeFromJsonElement<CandlesPush>(payload)
                return HermesCandleEvent.Candles(push.feedId, push.data)
            }
            if (message.name == NAME_ERROR_RESPONSE || message.name == NAME_ERROR_MESSAGE) {
                val error = message.payload?.let { hermesJson.decodeFromJsonElement<ErrorPayload>(it) }
                L.e("hermes ${envelope.channel} error: ${error?.code} ${error?.description}")
                val code = error?.code.orEmpty()
                if (envelope.channel != CHANNEL_SUBSCRIBE) {
                    return null
                }
                if (code == HERMES_ERROR_ALREADY_SUBSCRIBED) {
                    return null
                }
                val feedId = (message.reqid as? JsonPrimitive)?.longOrNull?.let { subscriptions[it] }
                return HermesCandleEvent.FeedError(feedId, permanent = code in PERMANENT_FEED_ERRORS)
            }
            return null
        } catch (e: SerializationException) {
            L.e(e)
            return null
        }
    }

    @Serializable
    private data class CandlesPush(
        val feedId: String? = null,
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

        val PERMANENT_FEED_ERRORS = setOf("invalid.request", "not.found", "unauthorized")

        const val INBOUND_CAPACITY = 256

        // Hermes drops sessions idle for 300 s.
        const val PING_INTERVAL_MS = 120_000L
        const val CLOSE_NORMAL = 1000
    }
}
