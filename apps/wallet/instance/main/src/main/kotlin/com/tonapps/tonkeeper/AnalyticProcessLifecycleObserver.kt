package com.tonapps.tonkeeper

import android.content.Context
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.tonapps.async.Async
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.LaunchApp.LaunchAppAppIcon
import com.tonapps.bus.generated.Events.LaunchApp.LaunchAppPushPermission
import com.tonapps.bus.generated.Events.LaunchApp.LaunchAppTheme
import com.tonapps.core.flags.WalletFeatureKey
import com.tonapps.extensions.areNotificationsEnabled
import com.tonapps.tonkeeper.core.LauncherIcon
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AnalyticProcessLifecycleObserver(
    private val context: Context,
) : DefaultLifecycleObserver, KoinComponent {

    private val api: API by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val unifiedAccountRepository: UnifiedAccountRepository by inject()
    private val environment: Environment by inject()

    override fun onStart(owner: LifecycleOwner) {
        Async.defaultScope().launch {
            api.configFlow.first { !it.empty }
            AnalyticsHelper.Default.events.launchApp.launchApp(
                theme = currentLaunchAppTheme(),
                appIcon = currentLaunchAppIcon(),
                walletsCount = unifiedAccountRepository.getWalletsCount(),
                pushPermission = currentLaunchAppPushPermission(),
                featureFlags = WalletFeatureKey.asAnalyticsProps(
                    mapOf(WalletFeatureKey.IS_MULTICHAIN_ENABLED to api.isMultichainAvailable())
                ),
            )
        }
    }

    private fun currentLaunchAppTheme(): LaunchAppTheme? {
        val theme = settingsRepository.theme
        return when {
            theme.isSystem -> if (settingsRepository.isLightTheme) {
                LaunchAppTheme.SystemLight
            } else {
                LaunchAppTheme.SystemDark
            }
            theme.key == "blue" -> LaunchAppTheme.DeepBlue
            theme.key == "dark" -> LaunchAppTheme.Dark
            theme.key == "light" -> LaunchAppTheme.Light
            else -> null
        }
    }

    private fun currentLaunchAppPushPermission(): LaunchAppPushPermission {
        return when {
            !environment.isGooglePlayServicesAvailable -> LaunchAppPushPermission.Unsupported
            context.areNotificationsEnabled() -> LaunchAppPushPermission.Granted
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !settingsRepository.pushPermissionRequested -> LaunchAppPushPermission.NotRequested
            else -> LaunchAppPushPermission.Denied
        }
    }

    private fun currentLaunchAppIcon(): LaunchAppAppIcon? {
        return when (LauncherIcon.entries.firstOrNull { it.isEnabled(context) }) {
            LauncherIcon.Default -> LaunchAppAppIcon.Default
            LauncherIcon.Accent -> LaunchAppAppIcon.Accent
            LauncherIcon.Dark -> LaunchAppAppIcon.Dark
            LauncherIcon.Light -> LaunchAppAppIcon.Light
            null -> null
        }
    }
}
