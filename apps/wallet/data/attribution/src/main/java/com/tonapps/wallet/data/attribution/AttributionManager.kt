package com.tonapps.wallet.data.attribution

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import com.tonapps.async.Async
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.InstallApp.InstallAppProvider
import com.tonapps.log.L
import com.tonapps.wallet.data.attribution.branch.BranchAttribution
import com.tonapps.wallet.data.attribution.entities.AttributionEvent
import com.tonapps.wallet.data.attribution.entities.AttributionProvider
import com.tonapps.wallet.data.attribution.entities.AttributionTouch
import com.tonapps.wallet.data.attribution.linkrunner.LinkrunnerAttribution
import com.tonapps.wallet.data.attribution.linkrunner.LinkrunnerInstallAttribution
import com.tonapps.wallet.data.attribution.source.AttributionTouchesDataSource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class AttributionManager(private val context: Context) {

    private val touchesDataSource = AttributionTouchesDataSource(context)
    private val branch = BranchAttribution()
    private val linkrunner = LinkrunnerAttribution(context)
    private val deepLinks = Channel<Uri>(Channel.CONFLATED)
    private var lastInterceptedLink: Uri? = null
    private var lastInterceptedAt = 0L
    private var linkrunnerInitialization: Job? = null
    private var delayedLinkrunnerInitialization: Job? = null

    val deepLinkFlow: Flow<Uri> = deepLinks.receiveAsFlow()

    fun initialize(application: Application, config: AttributionConfig) {
        branch.initialize(application, config.branchKey, config.isDebug)
        val initialization = Async.ioScope().launch(start = CoroutineStart.LAZY) {
            val isLinkrunnerInitialized = linkrunner.initialize(
                token = config.linkrunnerToken,
                isDebug = config.isDebug,
            )
            if (isLinkrunnerInitialized && !touchesDataSource.isLinkrunnerInstallResolved) {
                resolveLinkrunnerInstall()
            }
        }
        linkrunnerInitialization = initialization
        delayedLinkrunnerInitialization = Async.ioScope().launch(start = CoroutineStart.LAZY) {
            delay(LINKRUNNER_INITIALIZATION_DELAY_MILLIS)
            initialization.start()
        }
    }

    fun isAttributionLink(uri: Uri): Boolean {
        return linkrunner.isLinkrunnerLink(uri) || branch.isBranchLink(uri)
    }

    fun interceptLink(uri: Uri): Boolean {
        if (linkrunner.isLinkrunnerLink(uri)) {
            linkrunnerInitialization?.start()
            if (!isRepeatedLink(uri)) {
                Async.ioScope().launch { onClick(linkrunner.open(uri)) }
            }
            return true
        }
        return branch.isBranchLink(uri)
    }

    fun onActivityStart(activity: Activity) {
        branch.startSession(activity, ::onClick)
        delayedLinkrunnerInitialization?.start()
    }

    fun onActivityNewIntent(activity: Activity, intent: Intent) {
        branch.restartSession(activity, intent, ::onClick)
    }

    private fun isRepeatedLink(uri: Uri): Boolean {
        val now = SystemClock.elapsedRealtime()
        val isRepeated = uri == lastInterceptedLink && now - lastInterceptedAt < REPEATED_LINK_WINDOW_MILLIS
        lastInterceptedLink = uri
        lastInterceptedAt = now
        return isRepeated
    }

    private fun onClick(click: AttributedClick) {
        L.d(TAG, "touch: ${click.touch}, deep link: ${click.deepLink}")
        click.deepLink?.let(deepLinks::trySend)
        Async.ioScope().launch {
            touchesDataSource.add(click.touch)
            if (click.touch.event == AttributionEvent.Install) {
                reportInstall(click.touch)
            }
        }
    }

    private fun reportInstall(touch: AttributionTouch) {
        if (!touchesDataSource.markInstallReported() || !isFreshInstall()) {
            return
        }
        AnalyticsHelper.Default.eventsTaggedWith(touch.campaignUtm).installApp.installAttribution(
            provider = when (touch.provider) {
                AttributionProvider.Branch -> InstallAppProvider.Branch
                AttributionProvider.Linkrunner -> InstallAppProvider.Linkrunner
            },
            campaignId = touch.campaignId,
            campaignName = touch.campaignName,
        )
    }

    private fun isFreshInstall(): Boolean {
        return try {
            val firstInstallTime = context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime
            System.currentTimeMillis() - firstInstallTime <= FRESH_INSTALL_WINDOW_MILLIS
        } catch (e: PackageManager.NameNotFoundException) {
            L.e(e)
            false
        }
    }

    private suspend fun resolveLinkrunnerInstall() {
        var isAnswered = false
        for (delayMillis in LINKRUNNER_INSTALL_DELAYS_MILLIS) {
            delay(delayMillis)
            when (val attribution = linkrunner.installAttribution()) {
                is LinkrunnerInstallAttribution.Attributed -> {
                    L.d(TAG, "install touch: ${attribution.touch}")
                    touchesDataSource.add(attribution.touch)
                    touchesDataSource.isLinkrunnerInstallResolved = true
                    reportInstall(attribution.touch)
                    return
                }
                LinkrunnerInstallAttribution.NotAttributed -> isAnswered = true
                LinkrunnerInstallAttribution.Unavailable -> Unit
            }
        }
        if (isAnswered) {
            touchesDataSource.isLinkrunnerInstallResolved = true
        }
    }

    private companion object {
        const val TAG = "Attribution"
        const val REPEATED_LINK_WINDOW_MILLIS = 2_000L
        const val LINKRUNNER_INITIALIZATION_DELAY_MILLIS = 5_000L
        const val FRESH_INSTALL_WINDOW_MILLIS = 7L * 24 * 60 * 60 * 1000
        val LINKRUNNER_INSTALL_DELAYS_MILLIS = longArrayOf(3_000L, 10_000L, 30_000L)
    }
}
