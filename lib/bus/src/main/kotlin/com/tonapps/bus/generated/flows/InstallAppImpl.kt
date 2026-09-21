package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events

/**
 * Auto-generated from OpenAPI analytics schemas.
 * Do not edit manually — re-run the generator instead.
 */
class InstallAppImpl(
    private val eventExecutor: EventExecutor,
) : Events.InstallApp {

    private fun trackEvent(name: String, params: Map<String, Any>) {
        eventExecutor.trackEvent(name, params)
    }

    /**
     * install_app
     *
     * Sent once on the very first app launch after installation. Implemented by storing a firstLaunchTS flag in local storage. iOS only - Android uses legacy 'firstOpen' event instead of install_app.
     */
    @AnyThread
    override fun installApp(referrer: String?, deeplink: String?, installerStore: String?) {
        val props = mutableMapOf<String, Any>()
        referrer?.let { props["referrer"] = it }
        deeplink?.let { props["deeplink"] = it }
        installerStore?.let { props["installerStore"] = it }
        trackEvent("install_app", props)
    }
}
