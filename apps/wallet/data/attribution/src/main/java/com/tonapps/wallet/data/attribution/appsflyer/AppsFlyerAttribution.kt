package com.tonapps.wallet.data.attribution.appsflyer

import android.app.Activity
import android.app.Application
import android.net.Uri
import com.appsflyer.AppsFlyerLib
import com.appsflyer.share.AppsFlyerConversionListener
import com.appsflyer.share.deeplink.DeepLinkResult
import com.tonapps.async.Async
import com.tonapps.log.L
import com.tonapps.wallet.data.attribution.AttributionDeepLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

internal class AppsFlyerAttribution {

    private val lock = Any()
    private var isInitialized = false
    private var customerUserId: String? = null
    private var isStartPending = false

    private val awaitedLinkColdStart = AtomicReference<Boolean?>(null)

    fun initialize(
        application: Application,
        devKey: String,
        isDebug: Boolean,
        installReferrer: suspend () -> String?,
        onAppLink: (AttributionDeepLink) -> Unit,
        onInstall: (AppsFlyerInstall) -> Unit,
    ) {
        if (devKey.isBlank()) {
            return
        }
        val appsFlyer = AppsFlyerLib.getInstance()
        appsFlyer.setOneLinkCustomDomain(BRANDED_ONELINK_HOST)
        appsFlyer.init(devKey, ConversionListener(onInstall), application)
        appsFlyer.setDebugLog(isDebug)
        appsFlyer.registerSessionReadyListener { onSessionReady() }
        appsFlyer.subscribeForDeepLink { result -> onDeepLinking(result, installReferrer, onAppLink) }
        isInitialized = true
    }

    fun setCustomerUserId(customerUserId: String) {
        val isStartAwaited = synchronized(lock) {
            this.customerUserId = customerUserId
            isStartPending.also { isStartPending = false }
        }
        if (isStartAwaited) {
            start(customerUserId)
        }
    }

    fun isOneLink(uri: Uri): Boolean {
        return isOneLinkHost(uri.host) && (uri.scheme == "https" || uri.scheme == "http")
    }

    fun isResolvedBySdk(uri: Uri): Boolean {
        return isOneLink(uri) && isSdkResolvedOneLink(uri.pathSegments, uri.getQueryParameter(DEEP_LINK_VALUE_PARAM))
    }

    fun awaitSdkResolution(isColdStart: Boolean) {
        awaitedLinkColdStart.set(isColdStart)
    }

    fun appLinkOf(oneLink: Uri): String {
        return appsFlyerAppLink(appsFlyerValuesOf(oneLink.queryParameterNames.associateWith(oneLink::getQueryParameter)))
    }

    fun collectLaunchSource(activity: Activity) {
        if (isInitialized) {
            AppsFlyerLib.getInstance().collectDataFromLauncherActivity(activity)
        }
    }

    private fun onSessionReady() {
        val knownCustomerUserId = synchronized(lock) {
            customerUserId.also { isStartPending = it == null }
        }
        knownCustomerUserId?.let(::start)
    }

    private fun onDeepLinking(
        result: DeepLinkResult,
        installReferrer: suspend () -> String?,
        onAppLink: (AttributionDeepLink) -> Unit,
    ) {
        val deepLink = result.deepLink
        val isDeferred = deepLink?.isDeferred == true
        val clickColdStart = if (isDeferred) {
            null
        } else {
            awaitedLinkColdStart.getAndSet(null)
        }
        L.d(TAG, "deep link: ${result.status}, deferred: $isDeferred, awaited click: ${clickColdStart != null}, value: ${deepLink?.deepLinkValue}")
        if (result.status != DeepLinkResult.Status.FOUND || deepLink == null || !(isDeferred || clickColdStart != null)) {
            return
        }
        val linkValues = appsFlyerValuesOf(deepLink.clickEvent.keys().asSequence().associateWith(deepLink::getStringValue))
        if (!isDeferred) {
            onAppLink(AttributionDeepLink(Uri.parse(appsFlyerAppLink(linkValues)), isColdStart = clickColdStart == true, isDeferred = false))
            return
        }
        Async.ioScope().launch {
            val appLink = Uri.parse(appsFlyerDeferredAppLink(linkValues, readInstallReferrer(installReferrer)))
            onAppLink(AttributionDeepLink(appLink, isColdStart = true, isDeferred = true))
        }
    }

    private suspend fun readInstallReferrer(installReferrer: suspend () -> String?): String? {
        return try {
            withTimeoutOrNull(INSTALL_REFERRER_TIMEOUT_MS) { installReferrer().orEmpty() } ?: run {
                L.d(TAG, "install referrer timed out")
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e)
            null
        }
    }

    private class ConversionListener(
        private val onInstall: (AppsFlyerInstall) -> Unit,
    ) : AppsFlyerConversionListener {

        override fun onConversionDataSuccess(conversionData: Map<String, Any>?) {
            val install = conversionData?.let(::appsFlyerInstallOf)
            L.d(TAG, "conversion data: ${conversionData?.get(CONVERSION_STATUS_PARAM)}, install: $install")
            install?.let(onInstall)
        }

        override fun onConversionDataFail(errorMessage: String?) {
            L.d(TAG, "conversion data failed: $errorMessage")
        }
    }

    private fun start(customerUserId: String) {
        val appsFlyer = AppsFlyerLib.getInstance()
        appsFlyer.setCustomerUserId(customerUserId)
        appsFlyer.start()
    }

    private companion object {
        const val TAG = "AppsFlyer"
        const val INSTALL_REFERRER_TIMEOUT_MS = 3_000L
    }
}
