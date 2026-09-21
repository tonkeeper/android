package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class PushClickImpl(
    private val eventExecutor: EventExecutor,
) : Events.PushClick {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * push_click
     *
     * User tapped a push notification to open the app (deep link). Mobile native clients merge `schema_version`, `firebase_user_id`, `platform`, and optional `store_country_code` / `device_country_code` from `AnalyticsEventMobileNative`. Android usually includes `push_id` and/or `deep_link`; iOS may omit them.
     */
    @AnyThread
    override fun pushClick(pushId: String?, deepLink: String?) {
        val props = mutableMapOf<String, Any>()
        pushId?.let { props["push_id"] = it }
        deepLink?.let { props["deep_link"] = it }
        trackEvent("push_click", props)
    }
}
