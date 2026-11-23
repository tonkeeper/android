package com.tonapps.wallet.data.attribution

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.tonapps.async.Async
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.InstallApp.InstallAppProvider
import com.tonapps.log.L
import com.tonapps.wallet.data.attribution.appsflyer.AppsFlyerAttribution
import com.tonapps.wallet.data.attribution.appsflyer.AppsFlyerInstall
import com.tonapps.wallet.data.attribution.source.InstallAttributionDataSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class AttributionManager(private val context: Context) {

    private val installDataSource = InstallAttributionDataSource(context)
    private val installReporter = InstallAttributionReporter(
        markInstallReported = installDataSource::markInstallReported,
        firstInstallTime = ::firstInstallTime,
    )
    private val appsFlyer = AppsFlyerAttribution()
    private val deepLinks = Channel<AttributionDeepLink>(Channel.BUFFERED)

    val deepLinkFlow: Flow<AttributionDeepLink> = deepLinks.receiveAsFlow()

    fun initialize(application: Application, config: AttributionConfig) {
        appsFlyer.initialize(
            application = application,
            devKey = config.appsFlyerDevKey,
            isDebug = config.isDebug,
            installReferrer = config.installReferrer,
            onAppLink = ::openAppLink,
            onInstall = ::onAppsFlyerInstall,
        )
    }

    fun isAttributionLink(uri: Uri): Boolean {
        return appsFlyer.isOneLink(uri) || uri.isRemovedServiceLink()
    }

    fun interceptLink(uri: Uri, isColdStart: Boolean): Boolean {
        if (!appsFlyer.isOneLink(uri)) {
            return uri.isRemovedServiceLink()
        }
        if (appsFlyer.isResolvedBySdk(uri)) {
            appsFlyer.awaitSdkResolution(isColdStart)
        } else {
            openAppLink(AttributionDeepLink(Uri.parse(appsFlyer.appLinkOf(uri)), isColdStart, isDeferred = false))
        }
        return true
    }

    fun setCustomerUserId(customerUserId: String) {
        appsFlyer.setCustomerUserId(customerUserId)
    }

    fun onLauncherActivityCreated(activity: Activity) {
        appsFlyer.collectLaunchSource(activity)
    }

    fun withPrivateLinkHidden(activity: Activity, block: () -> Unit) {
        val intent = activity.intent
        val link = intent?.data
        if (link == null || isAttributionLink(link)) {
            block()
            return
        }
        activity.intent = Intent(intent).setData(null)
        try {
            block()
        } finally {
            activity.intent = intent
        }
    }

    private fun openAppLink(link: AttributionDeepLink) {
        deepLinks.trySend(link)
    }

    private fun onAppsFlyerInstall(install: AppsFlyerInstall) {
        Async.ioScope().launch {
            installReporter.reportOnce {
                AnalyticsHelper.Default.eventsTaggedWith(install.utm).installApp.installAttribution(
                    attributionStatus = install.status,
                    provider = InstallAppProvider.Appsflyer,
                    campaignId = install.campaignId,
                    campaignName = install.campaignName,
                )
            }
        }
    }

    private fun firstInstallTime(): Long? {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
        } catch (e: PackageManager.NameNotFoundException) {
            L.e(e)
            null
        }
    }

    private fun Uri.isRemovedServiceLink(): Boolean {
        if (host?.lowercase() in REMOVED_SERVICE_HOSTS) {
            return true
        }
        return scheme == APP_SCHEME && host == BRANCH_FALLBACK_HOST && getQueryParameter(BRANCH_LINK_CLICK_ID) != null
    }

    private companion object {
        const val APP_SCHEME = "tonkeeper"
        const val BRANCH_FALLBACK_HOST = "open"
        const val BRANCH_LINK_CLICK_ID = "link_click_id"
        val REMOVED_SERVICE_HOSTS = setOf(
            "x1zxr.app.link",
            "x1zxr-alternate.app.link",
            "link.tonkeeper.com",
        )
    }
}
