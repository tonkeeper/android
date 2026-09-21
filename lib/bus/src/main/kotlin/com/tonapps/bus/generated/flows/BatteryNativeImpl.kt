package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeSize
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeType

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class BatteryNativeImpl(
    private val eventExecutor: EventExecutor,
) : Events.BatteryNative {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * battery_open
     *
     * User opens the Battery purchase screen
     */
    @AnyThread
    override fun batteryOpen(from: BatteryNativeFrom) {
        trackEvent("battery_open", hashMapOf("from" to from.key))
    }

    /**
     * battery_select
     *
     * User selected a battery pack/amount and clicked continue
     */
    @AnyThread
    override fun batterySelect(
        from: BatteryNativeFrom,
        type: BatteryNativeType,
        size: BatteryNativeSize,
        promo: String?,
        jetton: String?
    ) {
        val props = hashMapOf<String, Any>(
            "from" to from.key,
            "type" to type.key,
            "size" to size.key
        )
        promo?.let { props["promo"] = it }
        jetton?.let { props["jetton"] = it }
        trackEvent("battery_select", props)
    }

    /**
     * battery_success
     *
     * User successfully completed a battery purchase
     */
    @AnyThread
    override fun batterySuccess(
        from: BatteryNativeFrom,
        type: BatteryNativeType,
        size: BatteryNativeSize,
        promo: String?,
        jetton: String?
    ) {
        val props = hashMapOf<String, Any>(
            "from" to from.key,
            "type" to type.key,
            "size" to size.key
        )
        promo?.let { props["promo"] = it }
        jetton?.let { props["jetton"] = it }
        trackEvent("battery_success", props)
    }
}
