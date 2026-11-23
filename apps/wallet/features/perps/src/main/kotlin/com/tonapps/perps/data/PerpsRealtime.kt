package com.tonapps.perps.data

import com.tonapps.log.L
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.realtime.RealtimeSignal
import com.tonapps.wallet.data.multichain.realtime.McWalletRealtimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

const val PERPS_SLOW_POLL_MS = 30_000L

private const val CHANNEL_PREFIX = "perps:"
private const val SUPPORTED_ENVELOPE_VERSION = 1
private const val EVENT_MARKET = "perps.market"
private const val EVENT_ACCOUNT = "perps.account"
private const val EVENT_DEPOSIT = "perps.deposit"
private const val EVENT_WITHDRAWAL = "perps.withdrawal"
private const val EVENT_BUFFER = 64
private const val DEMAND_LINGER_MS = 5_000L
private const val EVENT_SPACING_MS = 300L

enum class PerpsWake {
    EVENT,
    RESUBSCRIBED,
    SILENCE,
    TICK,
}

fun Flow<PerpsWake>.spacedEvents(): Flow<PerpsWake> = conflate().transform { wake ->
    emit(wake)
    if (wake == PerpsWake.EVENT) {
        delay(EVENT_SPACING_MS)
    }
}

sealed interface PerpsRealtimeEvent {
    val walletId: String

    data class Market(override val walletId: String, val marketIndex: Int) : PerpsRealtimeEvent
    data class Account(override val walletId: String) : PerpsRealtimeEvent
    data class Deposit(override val walletId: String) : PerpsRealtimeEvent
    data class Withdrawal(override val walletId: String) : PerpsRealtimeEvent
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class PerpsRealtime(
    private val api: API,
    private val walletRealtime: McWalletRealtimeProvider,
    private val scope: CoroutineScope,
) {

    private val demandCount = MutableStateFlow(0)
    val subscribedWalletId: StateFlow<String?> field = MutableStateFlow(null)

    val events: SharedFlow<PerpsRealtimeEvent> field = MutableSharedFlow(
        extraBufferCapacity = EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val appliedSeq = HashMap<String, Long>()

    @Volatile
    private var channel: String? = null

    fun start() {
        scope.launch {
            combine(walletRealtime.connectedWalletId, demanded()) { walletId, isDemanded ->
                walletId.takeIf { isDemanded }
            }
                .distinctUntilChanged()
                .collect(::follow)
        }
        scope.launch {
            api.centrifuge.signals.collect(::handleSignal)
        }
    }

    fun isSubscribed(walletId: String): Boolean = subscribedWalletId.value == walletId

    fun demand(): Demand = Demand()

    fun wakeups(
        walletId: String,
        unsubscribedPeriodMs: Long,
        subscribedTimeoutMs: Long? = null,
        wakeOn: (PerpsRealtimeEvent) -> Boolean,
    ): Flow<PerpsWake> = subscribedWalletId
        .map { it == walletId }
        .distinctUntilChanged()
        .withIndex()
        .flatMapLatest { (index, subscribed) ->
            if (subscribed) {
                eventWakeups(walletId, subscribedTimeoutMs, catchUp = index > 0, wakeOn)
            } else {
                periodic(unsubscribedPeriodMs)
            }
        }
        .conflate()

    suspend fun <T : Any> pollUntil(
        walletId: String,
        unsubscribedPeriodMs: Long,
        subscribedTimeoutMs: Long?,
        wakeOn: (PerpsRealtimeEvent) -> Boolean,
        attempt: suspend () -> T?,
    ): T = coroutineScope {
        val wakes = wakeups(walletId, unsubscribedPeriodMs, subscribedTimeoutMs, wakeOn).produceIn(this)
        try {
            var result = attempt()
            while (result == null) {
                wakes.receive()
                result = attempt()
            }
            result
        } finally {
            wakes.cancel()
        }
    }

    inner class Demand internal constructor() {

        private var active = false
        private var closed = false

        @Synchronized
        fun set(active: Boolean) {
            if (closed || this.active == active) {
                return
            }
            this.active = active
            demandCount.update { count ->
                if (active) {
                    count + 1
                } else {
                    count - 1
                }
            }
        }

        @Synchronized
        fun close() {
            set(false)
            closed = true
        }
    }

    private fun demanded(): Flow<Boolean> = demandCount
        .map { it > 0 }
        .distinctUntilChanged()
        .flatMapLatest { wanted ->
            if (wanted) {
                flowOf(true)
            } else {
                flow {
                    delay(DEMAND_LINGER_MS)
                    emit(false)
                }
            }
        }
        .distinctUntilChanged()

    private fun eventWakeups(
        walletId: String,
        timeoutMs: Long?,
        catchUp: Boolean,
        wakeOn: (PerpsRealtimeEvent) -> Boolean,
    ): Flow<PerpsWake> {
        val matching = events.filter { it.walletId == walletId && wakeOn(it) }.map { PerpsWake.EVENT }
        return flow {
            if (catchUp) {
                emit(PerpsWake.RESUBSCRIBED)
            }
            if (timeoutMs == null) {
                emitAll(matching)
                return@flow
            }
            coroutineScope {
                val inbox = matching.conflate().produceIn(this)
                while (true) {
                    emit(withTimeoutOrNull(timeoutMs) { inbox.receive() } ?: PerpsWake.SILENCE)
                }
            }
        }
    }

    private fun periodic(periodMs: Long): Flow<PerpsWake> = flow {
        while (true) {
            delay(periodMs)
            emit(PerpsWake.TICK)
        }
    }

    @Synchronized
    private fun follow(walletId: String?) {
        val next = walletId?.let { CHANNEL_PREFIX + it }
        val previous = channel
        if (previous == next) {
            return
        }
        channel = next
        if (previous != null) {
            api.centrifuge.unsubscribe(previous)
            subscribedWalletId.value = null
        }
        if (walletId != null && next != null) {
            api.centrifuge.subscribe(next) { api.realtimeTokens.perpsSubscriptionToken(walletId) }
        }
    }

    private fun handleSignal(signal: RealtimeSignal) {
        when (signal) {
            is RealtimeSignal.Subscribed -> if (signal.channel == channel) {
                subscribedWalletId.value = signal.channel.removePrefix(CHANNEL_PREFIX)
            }

            is RealtimeSignal.Unsubscribed -> if (signal.channel.startsWith(CHANNEL_PREFIX)) {
                subscribedWalletId.compareAndSet(signal.channel.removePrefix(CHANNEL_PREFIX), null)
            }

            is RealtimeSignal.Publication -> if (signal.channel == channel) {
                handlePublication(signal.channel.removePrefix(CHANNEL_PREFIX), signal.payload)
            }

            RealtimeSignal.DisabledByBackend -> Unit
        }
    }

    private fun handlePublication(walletId: String, payload: ByteArray) {
        val envelope = PerpsRealtimeEnvelope.parse(payload) ?: run {
            L.w("Perps realtime event is malformed")
            return
        }
        L.d("Perps realtime event: ${envelope.event} seq=${envelope.seq}")
        if (envelope.version != SUPPORTED_ENVELOPE_VERSION || envelope.walletId != walletId) {
            L.d("Perps realtime event dropped: v=${envelope.version} wallet=${envelope.walletId} expected=$walletId")
            return
        }
        val event = envelope.toEvent() ?: return
        if (markApplied(envelope.seqKey(), envelope.seq)) {
            events.tryEmit(event)
        }
    }

    private fun markApplied(key: String, seq: Long): Boolean {
        val previous = appliedSeq[key]
        if (previous != null && seq <= previous) {
            return false
        }
        appliedSeq[key] = seq
        return true
    }
}

@Serializable
internal data class PerpsRealtimeEnvelope(
    @SerialName("v") val version: Int,
    @SerialName("event") val event: String,
    @SerialName("wallet_id") val walletId: String,
    @SerialName("seq") val seq: Long,
    @SerialName("data") val data: Data? = null,
) {

    @Serializable
    internal data class Data(
        @SerialName("market_index") val marketIndex: Int? = null,
    )

    fun toEvent(): PerpsRealtimeEvent? = when (event) {
        EVENT_MARKET -> data?.marketIndex?.let { PerpsRealtimeEvent.Market(walletId, it) }
        EVENT_ACCOUNT -> PerpsRealtimeEvent.Account(walletId)
        EVENT_DEPOSIT -> PerpsRealtimeEvent.Deposit(walletId)
        EVENT_WITHDRAWAL -> PerpsRealtimeEvent.Withdrawal(walletId)
        else -> null
    }

    fun seqKey(): String = "$walletId:$event:${data?.marketIndex}"

    companion object {

        private val json = Json { ignoreUnknownKeys = true }

        fun parse(payload: ByteArray): PerpsRealtimeEnvelope? {
            return try {
                json.decodeFromString<PerpsRealtimeEnvelope>(payload.decodeToString())
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
