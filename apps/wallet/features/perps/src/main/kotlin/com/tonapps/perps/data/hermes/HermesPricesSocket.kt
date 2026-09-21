package com.tonapps.perps.data.hermes

import com.tonapps.log.L
import com.tonapps.wallet.api.core.HermesAPI
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicLong

sealed interface HermesPriceEvent {
    data object Opened : HermesPriceEvent
    data object Subscribed : HermesPriceEvent
    data class Prices(val prices: Map<String, BigDecimal>) : HermesPriceEvent

    /** The subscribe will never deliver on this connection — reconnecting cannot fix it. */
    data object Rejected : HermesPriceEvent
    data object Terminated : HermesPriceEvent
}

class HermesPricesSocket(private val hermes: HermesAPI) {

    // DROP_OLDEST is safe: every push is a full snapshot of the subscribed set.
    private val inbound = Channel<HermesPriceEvent>(INBOUND_CAPACITY, BufferOverflow.DROP_OLDEST)
    private val nextRequestId = AtomicLong(0L)

    @Volatile
    private var socket: WebSocket? = null

    @Volatile
    private var subscribeRequestId: Long = 0L

    val events: ReceiveChannel<HermesPriceEvent>
        get() = inbound

    private val listener = object : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            inbound.trySend(HermesPriceEvent.Opened)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            decode(text)?.let { inbound.trySend(it) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(CLOSE_NORMAL, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            inbound.trySend(HermesPriceEvent.Terminated)
            inbound.close()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            L.e(t)
            inbound.trySend(HermesPriceEvent.Terminated)
            inbound.close()
        }
    }

    fun connect() {
        socket = hermes.connect(listener)
    }

    fun subscribe(tickers: List<String>) {
        val payload = buildJsonObject {
            putJsonArray("tickers") {
                for (ticker in tickers) {
                    add(ticker)
                }
            }
            put("cooldown", COOLDOWN)
        }
        subscribeRequestId = nextRequestId.incrementAndGet()
        send(CHANNEL_SUBSCRIBE, NAME_SUBSCRIBE, subscribeRequestId, payload)
    }

    suspend fun keepAlive() {
        while (true) {
            delay(PING_INTERVAL_MS)
            send(CHANNEL_PING, NAME_PING, nextRequestId.incrementAndGet())
        }
    }

    fun close() {
        socket?.close(CLOSE_NORMAL, null)
        socket = null
        inbound.close()
    }

    private fun send(channel: String, name: String, reqid: Long, payload: JsonObject? = null) {
        val socket = socket ?: return
        socket.send(hermesFrame(channel, name, reqid, payload))
    }

    private fun decode(text: String): HermesPriceEvent? {
        try {
            val envelope = hermesJson.decodeFromString<InboundEnvelope>(text)
            val message = envelope.message ?: return null
            if (envelope.channel == CHANNEL_PRICES && message.name == NAME_PRICES_PUSH) {
                val payload = message.payload ?: return null
                return HermesPriceEvent.Prices(
                    hermesJson.decodeFromJsonElement<PricesPush>(payload).toPrices()
                )
            }
            if (message.name == NAME_SUBSCRIBE_RESPONSE) {
                val reqid = runCatching { message.reqid?.jsonPrimitive?.longOrNull }.getOrNull()
                if (reqid == subscribeRequestId) {
                    return HermesPriceEvent.Subscribed
                }
                return null
            }
            if (message.name == NAME_ERROR_RESPONSE || message.name == NAME_ERROR_MESSAGE) {
                val error = message.payload?.let { hermesJson.decodeFromJsonElement<ErrorPayload>(it) }
                L.e("hermes prices ${envelope.channel} error: ${error?.code} ${error?.description}")
                if (envelope.channel != CHANNEL_SUBSCRIBE) {
                    return null
                }
                val code = error?.code.orEmpty()
                if (code == HERMES_ERROR_ALREADY_SUBSCRIBED) {
                    return HermesPriceEvent.Subscribed
                }
                if (code in PERMANENT_ERRORS) {
                    return HermesPriceEvent.Rejected
                }
                return HermesPriceEvent.Terminated
            }
            return null
        } catch (e: SerializationException) {
            L.e(e)
            return null
        }
    }

    private fun PricesPush.toPrices(): Map<String, BigDecimal> {
        val decoded = mutableMapOf<String, BigDecimal>()
        for (entry in markPrices) {
            val ticker = entry.ticker?.takeIf { it.isNotBlank() } ?: continue
            val price = entry.markPrice?.let { runCatching { BigDecimal(it) }.getOrNull() } ?: continue
            if (price.signum() <= 0) {
                continue
            }
            decoded[ticker.uppercase()] = price
        }
        return decoded
    }

    @Serializable
    private data class PricesPush(
        val markPrices: List<PriceEntry> = emptyList(),
    )

    @Serializable
    private data class PriceEntry(
        val ticker: String? = null,
        val markPrice: String? = null,
    )

    private companion object {
        const val CHANNEL_PING = "ping"
        const val CHANNEL_SUBSCRIBE = "subscribeMarkPrices"
        const val CHANNEL_PRICES = "markPrices"

        const val NAME_PING = "ping"
        const val NAME_SUBSCRIBE = "subscribeMarkPricesRequest"
        const val NAME_SUBSCRIBE_RESPONSE = "subscribeMarkPricesResponse"
        const val NAME_PRICES_PUSH = "markPricesMessage"
        const val NAME_ERROR_RESPONSE = "errorResponse"
        const val NAME_ERROR_MESSAGE = "errorMessage"

        const val COOLDOWN = "PT2S"

        val PERMANENT_ERRORS = setOf("unauthorized", "invalid.request", "not.found")

        const val INBOUND_CAPACITY = 64

        // Hermes drops sessions idle for 300 s.
        const val PING_INTERVAL_MS = 120_000L
        const val CLOSE_NORMAL = 1000
    }
}
