package com.tonapps.bus.core

import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.core.contract.UtmParameters

internal class UtmEventExecutor(
    private val executor: EventExecutor,
    private val utm: UtmParameters,
) : EventExecutor {

    override fun trackEvent(eventName: String, props: Map<String, Any>) {
        executor.trackEvent(eventName, props + utm.toEventProps())
    }
}
