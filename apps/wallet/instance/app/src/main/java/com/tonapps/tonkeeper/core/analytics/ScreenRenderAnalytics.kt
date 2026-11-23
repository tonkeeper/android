package com.tonapps.tonkeeper.core.analytics

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.ScreenRender.ScreenRenderErrorKind
import com.tonapps.bus.generated.Events.ScreenRender.ScreenRenderNetworkType
import com.tonapps.bus.generated.Events.ScreenRender.ScreenRenderOutcome
import com.tonapps.bus.generated.Events.ScreenRender.ScreenRenderScreen
import com.tonapps.bus.generated.Events.ScreenRender.ScreenRenderThermalState
import com.tonapps.bus.generated.Events.ScreenRender.ScreenRenderTrigger
import com.tonapps.bus.generated.Events.ScreenRender.ScreenRenderWalletMode
import com.tonapps.bus.screenrender.ScreenRenderErrorKind as RenderErrorKind
import com.tonapps.bus.screenrender.ScreenRenderId
import com.tonapps.bus.screenrender.ScreenRenderOutcome as RenderOutcome
import com.tonapps.bus.screenrender.ScreenRenderReport
import com.tonapps.bus.screenrender.ScreenRenderReporter
import com.tonapps.bus.screenrender.ScreenRenderTrigger as RenderTrigger
import com.tonapps.wallet.data.account.AccountRepository

class ScreenRenderAnalytics(
    context: Context,
    private val accountRepository: () -> AccountRepository,
) : ScreenRenderReporter {

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)

    override fun report(report: ScreenRenderReport) {
        val wallet = (accountRepository().selectedStateFlow.value as? AccountRepository.SelectedState.Wallet)?.wallet ?: return
        val networkType = networkType()
        AnalyticsHelper.Default.events.screenRender.screenContentRendered(
            screen = report.screen.toEvent(),
            walletMode = walletMode(wallet.type),
            trigger = report.trigger.toEvent(),
            outcome = report.outcome.toEvent(),
            errorKind = report.errorKind?.toEvent(networkType),
            isFirstOpen = report.isFirstOpen,
            cachedMs = report.cachedMs,
            freshMs = report.freshMs,
            screenCreatedMs = report.createdMs,
            screenWillShowMs = report.willShowMs,
            screenShownMs = report.shownMs,
            requestMs = report.requestMs,
            marks = report.marks.takeIf { it.isNotEmpty() }?.joinToString(";") { "${it.name}:${it.ms}" },
            pausedMs = report.pausedMs,
            hadPasscode = report.hadPasscode,
            networkType = networkType,
            isLowPower = powerManager?.isPowerSaveMode == true,
            thermalState = thermalState(),
        )
    }

    private fun networkType(): ScreenRenderNetworkType {
        val capabilities = connectivityManager?.activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
            ?: return ScreenRenderNetworkType.None
        return when {
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> ScreenRenderNetworkType.None
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ScreenRenderNetworkType.Wifi
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ScreenRenderNetworkType.Cellular
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ScreenRenderNetworkType.Wired
            else -> ScreenRenderNetworkType.Other
        }
    }

    private fun thermalState(): ScreenRenderThermalState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return ScreenRenderThermalState.Nominal
        }
        return when (powerManager?.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_MODERATE -> ScreenRenderThermalState.Fair
            PowerManager.THERMAL_STATUS_SEVERE -> ScreenRenderThermalState.Serious
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ScreenRenderThermalState.Critical
            else -> ScreenRenderThermalState.Nominal
        }
    }

    private fun ScreenRenderId.toEvent(): ScreenRenderScreen = when (this) {
        ScreenRenderId.Portfolio -> ScreenRenderScreen.Portfolio
        ScreenRenderId.History -> ScreenRenderScreen.History
        ScreenRenderId.Trade -> ScreenRenderScreen.Trade
        ScreenRenderId.Browser -> ScreenRenderScreen.Browser
    }

    private fun RenderTrigger.toEvent(): ScreenRenderTrigger = when (this) {
        RenderTrigger.InApp -> ScreenRenderTrigger.InApp
        RenderTrigger.AppLaunch -> ScreenRenderTrigger.AppLaunch
        RenderTrigger.Deeplink -> ScreenRenderTrigger.Deeplink
        RenderTrigger.Push -> ScreenRenderTrigger.Push
        RenderTrigger.Auto -> ScreenRenderTrigger.Auto
    }

    private fun RenderOutcome.toEvent(): ScreenRenderOutcome = when (this) {
        RenderOutcome.Fresh -> ScreenRenderOutcome.Fresh
        RenderOutcome.CachedOnly -> ScreenRenderOutcome.CachedOnly
        RenderOutcome.Failed -> ScreenRenderOutcome.Failed
        RenderOutcome.Abandoned -> ScreenRenderOutcome.Abandoned
        RenderOutcome.Timeout -> ScreenRenderOutcome.Timeout
    }

    // Loaders report a lost connection as a generic network failure, and some swallow the error altogether;
    // the current connection tells an offline device apart.
    private fun RenderErrorKind.toEvent(networkType: ScreenRenderNetworkType): ScreenRenderErrorKind {
        val isOffline = networkType == ScreenRenderNetworkType.None
        return when (this) {
            RenderErrorKind.Timeout -> ScreenRenderErrorKind.Timeout
            RenderErrorKind.Offline -> ScreenRenderErrorKind.Offline
            RenderErrorKind.Network -> offlineOr(ScreenRenderErrorKind.Network, isOffline)
            RenderErrorKind.Server -> ScreenRenderErrorKind.Server
            RenderErrorKind.Decode -> ScreenRenderErrorKind.Decode
            RenderErrorKind.Unknown -> offlineOr(ScreenRenderErrorKind.Unknown, isOffline)
        }
    }

    private fun walletMode(type: WalletType): ScreenRenderWalletMode = when (type) {
        WalletType.Multichain -> ScreenRenderWalletMode.Multi
        else -> ScreenRenderWalletMode.Single
    }

    private fun offlineOr(kind: ScreenRenderErrorKind, isOffline: Boolean): ScreenRenderErrorKind {
        return if (isOffline) {
            ScreenRenderErrorKind.Offline
        } else {
            kind
        }
    }
}
