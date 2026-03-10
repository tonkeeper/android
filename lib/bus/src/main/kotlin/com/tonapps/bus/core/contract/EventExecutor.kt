package com.tonapps.bus.core.contract

interface EventExecutor {
    fun trackEvent(eventName: String, props: Map<String, Any> = emptyMap())
}
