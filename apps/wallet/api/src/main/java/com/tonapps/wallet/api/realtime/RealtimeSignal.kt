package com.tonapps.wallet.api.realtime

sealed interface RealtimeSignal {
    class Publication(val channel: String, val payload: ByteArray) : RealtimeSignal
    data class Subscribed(val channel: String, val resubscribed: Boolean) : RealtimeSignal
    data class Unsubscribed(val channel: String) : RealtimeSignal
    data object DisabledByBackend : RealtimeSignal
}
