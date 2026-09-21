package com.tonapps.wallet.data.multichain.realtime

import android.os.SystemClock
import com.tonapps.async.Async
import com.tonapps.core.flags.WalletFeature
import com.tonapps.extensions.AppLifecycleProvider
import com.tonapps.log.L
import com.tonapps.network.NetworkMonitor
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.realtime.RealtimeSignal
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val SUPPORTED_ENVELOPE_VERSION = 1
private const val EVENT_BALANCE_HINT = "balance.hint"
private const val EVENT_ACTIVITY_HINT = "activity.hint"
private const val WALLET_CHANNEL_PREFIX = "wallet:"
private val HINT_DEBOUNCE = 400.milliseconds
private val RESYNC_MIN_GAP = 15.seconds

@OptIn(FlowPreview::class)
class McWalletRealtimeProvider(
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val networkMonitor: NetworkMonitor,
    private val appLifecycleProvider: AppLifecycleProvider,
    private val api: API,
) {

    private val scope = Async.globalScope(Async.Io)

    private val disabledByBackend = MutableStateFlow(false)
    private val _subscribedWalletId = MutableStateFlow<String?>(null)
    val subscribedWalletId: StateFlow<String?> = _subscribedWalletId.asStateFlow()

    private val seqGuard = RealtimeSeqGuard()
    private val unsubscribedAt = mutableMapOf<String, Long>()

    @Volatile
    private var channel: String? = null

    private val balanceHintRelay = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val activityHintRelay = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val balanceHints: Flow<String> = balanceHintRelay.debounce(HINT_DEBOUNCE)
    val activityHints: Flow<String> = activityHintRelay.debounce(HINT_DEBOUNCE)

    fun start() {
        scope.launch {
            combine(
                appLifecycleProvider.observe,
                networkMonitor.isOnlineFlow,
                selectedMultichainWalletIdFlow(),
                disabledByBackend,
            ) { isForeground, isOnline, walletId, isDisabledByBackend ->
                walletId.takeIf {
                    isForeground && isOnline && !isDisabledByBackend && WalletFeature.Realtime.isEnabled
                }
            }
                .distinctUntilChanged()
                .collect { walletId ->
                    if (walletId == null) {
                        api.centrifuge.disconnect()
                    } else {
                        connect(walletId)
                    }
                }
        }
        scope.launch {
            api.centrifuge.signals.collect(::handleSignal)
        }
    }

    fun isSubscribed(walletId: String): Boolean = subscribedWalletId.value == walletId

    private fun selectedMultichainWalletIdFlow(): Flow<String?> {
        return unifiedAccountRepository.selectedTonWalletFlow.map { it?.multichainWalletId }
    }

    private fun connect(walletId: String) {
        val next = WALLET_CHANNEL_PREFIX + walletId
        channel?.takeIf { it != next }?.let(api.centrifuge::unsubscribe)
        channel = next
        api.centrifuge.subscribe(next) { api.realtimeTokens.subscriptionToken(walletId) }
        api.centrifuge.connect()
    }

    private fun handleSignal(signal: RealtimeSignal) {
        when (signal) {
            is RealtimeSignal.Subscribed -> signal.channel.toWalletId()?.let { walletId ->
                _subscribedWalletId.value = walletId
                if (signal.resubscribed && missedEventsLikely(walletId)) {
                    resync(walletId)
                }
            }
            is RealtimeSignal.Unsubscribed -> signal.channel.toWalletId()?.let { walletId ->
                _subscribedWalletId.compareAndSet(walletId, null)
                unsubscribedAt[walletId] = SystemClock.elapsedRealtime()
            }
            is RealtimeSignal.Publication -> signal.channel.toWalletId()?.let { walletId ->
                handlePublication(walletId, signal.payload)
            }
            RealtimeSignal.DisabledByBackend -> disabledByBackend.value = true
        }
    }

    private fun String.toWalletId(): String? {
        return takeIf { it.startsWith(WALLET_CHANNEL_PREFIX) }?.removePrefix(WALLET_CHANNEL_PREFIX)
    }

    private fun missedEventsLikely(walletId: String): Boolean {
        val since = unsubscribedAt[walletId] ?: return true
        return SystemClock.elapsedRealtime() - since >= RESYNC_MIN_GAP.inWholeMilliseconds
    }

    private fun resync(walletId: String) {
        L.d("Realtime resync after reconnect: $walletId")
        balanceHintRelay.tryEmit(walletId)
        activityHintRelay.tryEmit(walletId)
    }

    private fun handlePublication(walletId: String, payload: ByteArray) {
        val envelope = RealtimeEnvelopeParser.parse(payload).getOrElse { error ->
            L.w(error, "Realtime envelope is malformed")
            return
        }
        L.d("Realtime event: ${envelope.event} seq=${envelope.seq}")
        if (envelope.version != SUPPORTED_ENVELOPE_VERSION || envelope.walletId != walletId) {
            L.d("Realtime event dropped: v=${envelope.version} wallet=${envelope.walletId} expected=$walletId")
            return
        }
        when (envelope.event) {
            EVENT_BALANCE_HINT -> if (seqGuard.markApplied(envelope.walletId, envelope.event, envelope.seq)) {
                balanceHintRelay.tryEmit(envelope.walletId)
            }
            EVENT_ACTIVITY_HINT -> if (seqGuard.markApplied(envelope.walletId, envelope.event, envelope.seq)) {
                activityHintRelay.tryEmit(envelope.walletId)
            }
            else -> L.d("Realtime event ignored: ${envelope.event}")
        }
    }
}
