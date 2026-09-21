package com.tonapps.wc.models

sealed interface WcPendingEvent {
    class Created(val topic: String, val pairingTopic: String) : WcPendingEvent
    class Disconnected(val topic: String) : WcPendingEvent
}
