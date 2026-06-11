package com.tonapps.settings.dev

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.tonapps.settings.dev.features.FeatureFlagsScreen
import com.tonapps.settings.dev.tooltips.TooltipsScreen
import kotlinx.serialization.Serializable
import ui.moon.MoonNav

@Serializable
sealed interface DevSettingsRoutes : NavKey {
    @Serializable
    data object FeatureFlags : DevSettingsRoutes

    @Serializable
    data object Tooltips : DevSettingsRoutes
}

@Composable
fun DevSettingsRouter(
    startRoute: String?,
    onClose: () -> Unit,
) {
    val initialRoute = when (startRoute) {
        ROUTE_TOOLTIPS -> DevSettingsRoutes.Tooltips
        else -> DevSettingsRoutes.FeatureFlags
    }
    val backStack = rememberNavBackStack(initialRoute)

    MoonNav(
        backStack = backStack,
    ) { key ->
        when (key) {
            is DevSettingsRoutes.FeatureFlags -> NavEntry(key) {
                FeatureFlagsScreen(
                    onBack = onClose,
                )
            }

            is DevSettingsRoutes.Tooltips -> NavEntry(key) {
                TooltipsScreen(
                    onBack = onClose,
                )
            }

            else -> throw IllegalStateException("Unknown key: $key")
        }
    }
}

const val ROUTE_FEATURE_FLAGS = "feature_flags"
const val ROUTE_TOOLTIPS = "tooltips"
