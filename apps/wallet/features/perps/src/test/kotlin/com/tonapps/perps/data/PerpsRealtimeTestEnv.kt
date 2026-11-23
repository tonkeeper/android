package com.tonapps.perps.data

import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.realtime.CentrifugeAPI
import com.tonapps.wallet.api.realtime.RealtimeSignal
import com.tonapps.wallet.data.multichain.realtime.McWalletRealtimeProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

internal class PerpsRealtimeTestEnv(scope: CoroutineScope) {

    val signals = MutableSharedFlow<RealtimeSignal>(extraBufferCapacity = SIGNAL_BUFFER)
    val connectedWalletId = MutableStateFlow<String?>(null)
    val subscribedChannels = mutableListOf<String>()
    val unsubscribedChannels = mutableListOf<String>()

    private val centrifuge = mockk<CentrifugeAPI> {
        every { signals } returns this@PerpsRealtimeTestEnv.signals
        every { subscribe(any(), any()) } answers { subscribedChannels += firstArg<String>() }
        every { unsubscribe(any()) } answers { unsubscribedChannels += firstArg<String>() }
    }

    private val api = mockk<API> {
        every { centrifuge } returns this@PerpsRealtimeTestEnv.centrifuge
        every { realtimeTokens } returns mockk(relaxed = true)
    }

    private val walletRealtime = mockk<McWalletRealtimeProvider> {
        every { connectedWalletId } returns this@PerpsRealtimeTestEnv.connectedWalletId
    }

    val realtime = PerpsRealtime(api, walletRealtime, scope).also { it.start() }

    fun connect(walletId: String): PerpsRealtime.Demand {
        connectedWalletId.value = walletId
        return realtime.demand().apply { set(true) }
    }

    fun confirmSubscription(walletId: String, resubscribed: Boolean = false) {
        signals.tryEmit(RealtimeSignal.Subscribed(channel(walletId), resubscribed))
    }

    fun dropSubscription(walletId: String) {
        signals.tryEmit(RealtimeSignal.Unsubscribed(channel(walletId)))
    }

    fun publish(
        walletId: String,
        event: String,
        seq: Long,
        data: String = "{}",
        version: Int = 1,
        envelopeWalletId: String = walletId,
    ) {
        val payload = """{"v":$version,"event":"$event","wallet_id":"$envelopeWalletId","seq":$seq,""" +
            """"ts":"2026-11-03T13:17:39.762114Z","data":$data}"""
        signals.tryEmit(RealtimeSignal.Publication(channel(walletId), payload.encodeToByteArray()))
    }

    fun publishMarket(walletId: String, marketIndex: Int, seq: Long) {
        publish(walletId, "perps.market", seq, data = """{"market_index":$marketIndex,"open":null,"orders":[]}""")
    }

    fun channel(walletId: String): String = "perps:$walletId"

    private companion object {
        const val SIGNAL_BUFFER = 64
    }
}
