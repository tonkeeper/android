package com.tonapps.bus.generated.flows

import androidx.annotation.AnyThread
import com.tonapps.bus.core.contract.EventExecutor
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.InstallApp.InstallAppAttributionStatus
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
     * Sent at most once per install, when the attribution service answered whether the install came from a campaign. The service is AppsFlyer (`provider = appsflyer`), tried on Android before iOS: Android sends the event from the conversion data the AppsFlyer SDK returns on the first launch (or on a later one if the app closed before it answered), for organic and non-organic installs alike, with attribution_status saying which. The SDK hands the conversion data back on every launch; the app sends the first answer with a known af_status and nothing after it. Debug builds send none, as AppsFlyer does not know their package. iOS does not send it.
Join it to install_app on user_id for one row per install. Three states: attribution_status = non_organic (a campaign link brought the install), organic (AppsFlyer matched no campaign; a store link with its own referrer tags still shows up in install_app.referrer), and no install_attribution at all (AppsFlyer never answered, iOS, or a build without the listener). A campaign click that opens an app already installed is deeplink_open, not this event. A campaign link that brought the install also reaches the app on its first launch and is sent as deeplink_open with from = deferred_link: count the install here and the deferred click there, never the two together. Not sent for an install older than seven days, which is a user updating into the first version with the SDK rather than a new install.
The campaign travels in the shared utm_* fields and in campaign_id / campaign_name, set only on non_organic rows. AppsFlyer passes the utm_* of the clicked OneLink in its conversion data; when the link has none, its media_source / af_channel / campaign stand in for utm_source / utm_medium / utm_campaign. Group by utm_campaign and fall back to campaign_name for a campaign link created without utm.
Legacy providers: Branch and Linkrunner were piloted on Android and dropped in favour of AppsFlyer; both were removed from the app in November 2026. Their rows (a handful of test installs) carry `provider = branch` / `linkrunner`, the Linkrunner campaign or Branch link in campaign_id / campaign_name, and utm_* only when the link had them (Branch's ~channel / ~feature / ~campaign stood in when it had none).

     */
    @AnyThread
    override fun installAttribution(
        attributionStatus: InstallAppAttributionStatus?,
        provider: InstallAppProvider,
        campaignId: String?,
        campaignName: String?
    ) {
        val props = hashMapOf<String, Any>("provider" to provider.key)
        attributionStatus?.let { props["attribution_status"] = it.key }
        campaignId?.let { props["campaign_id"] = it }
        campaignName?.let { props["campaign_name"] = it }
        trackEvent("install_attribution", props)
    }
}
