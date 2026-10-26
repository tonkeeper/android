package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.InstallApp.InstallAppProvider

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
     * Sent once on the very first app launch after installation. Implemented by storing a firstLaunchTS flag in local storage. Android fills referrer, deeplink and installerStore; iOS sends the event without them.
     */
    @AnyThread
    override fun installApp(referrer: String?, deeplink: String?, installerStore: String?) {
        val props = mutableMapOf<String, Any>()
        referrer?.let { props["referrer"] = it }
        deeplink?.let { props["deeplink"] = it }
        installerStore?.let { props["installerStore"] = it }
        trackEvent("install_app", props)
    }

    /**
     * install_attribution
     *
     * Sent at most once per install, when an attribution service (Branch or Linkrunner) matched the install to a click on its campaign link: on the first launch, or on the next one if the app closed before the service answered. When both services match the same install, only the first answer is sent. It tells a new user who came from a Branch or Linkrunner campaign link from one who did not: an install no service matched sends none (a store link with its own referrer tags shows up only in install_app.referrer), and a campaign click that opens an app already installed is deeplink_open, not this event. Not sent for an install older than seven days, which is a user updating into the first version with the attribution SDKs rather than a new install. Android only for now; iOS does not send it yet.
The campaign travels in the shared utm_* fields and in campaign_id / campaign_name. Branch keeps the utm_* of the clicked link in its click data, and when the link has none, its own ~channel / ~feature / ~campaign stand in for utm_source / utm_medium / utm_campaign. Linkrunner's install attribution carries no click utm_*: they are set only when the campaign deep link it resolves the install to has them, and the Linkrunner campaign is always in campaign_id / campaign_name. Group by utm_campaign and fall back to campaign_name for a campaign link created without utm.

     */
    @AnyThread
    override fun installAttribution(provider: InstallAppProvider, campaignId: String?, campaignName: String?) {
        val props = hashMapOf<String, Any>("provider" to provider.key)
        campaignId?.let { props["campaign_id"] = it }
        campaignName?.let { props["campaign_name"] = it }
        trackEvent("install_attribution", props)
    }
}
