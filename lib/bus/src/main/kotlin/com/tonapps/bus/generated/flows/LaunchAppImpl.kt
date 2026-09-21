package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.LaunchApp.LaunchAppAppIcon
import com.tonapps.bus.generated.Events.LaunchApp.LaunchAppPushPermission
import com.tonapps.bus.generated.Events.LaunchApp.LaunchAppTheme

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class LaunchAppImpl(
    private val eventExecutor: EventExecutor,
) : Events.LaunchApp {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * launch_app
     *
     * Sent every time the user opens the app.
     */
    @AnyThread
    override fun launchApp(
        theme: LaunchAppTheme?,
        appIcon: LaunchAppAppIcon?,
        walletsCount: Int?,
        pushPermission: LaunchAppPushPermission?,
        featureFlags: Map<String, Any>
    ) {
        val props = mutableMapOf<String, Any>()
        featureFlags.forEach { (key, value) -> props[key.take(40)] = value }
        theme?.let { props["theme"] = it.key }
        appIcon?.let { props["app_icon"] = it.key }
        walletsCount?.let { props["wallets_count"] = it }
        pushPermission?.let { props["push_permission"] = it.key }
        trackEvent("launch_app", props)
    }
}
