package com.tonapps.core

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import com.tonapps.apps.wallet.features.core.R
import com.tonapps.wallet.data.settings.SettingsRepository
import org.koin.android.ext.android.inject
import ui.theme.AppColorScheme
import ui.theme.MoonTheme
import ui.theme.appColorSchemeBlue
import ui.theme.appColorSchemeDark
import ui.theme.appColorSchemeLight
import uikit.base.BaseFragment

abstract class ComposableFragment : BaseFragment(R.layout.fragment_compose_host) {

    val settings: SettingsRepository by inject()

    private val Context.uiMode: Int
        get() = resources.configuration.uiMode

    private val Context.isDarkMode: Boolean
        get() = uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    @get:Composable
    private val theme: AppColorScheme
        get() {
            return when(settings.theme.key) {
                "blue" -> appColorSchemeBlue()
                "dark" -> appColorSchemeDark()
                "light" -> appColorSchemeLight()
                else -> if (requireContext().isDarkMode) appColorSchemeBlue() else appColorSchemeLight()
            }
        }

    fun setContent(content: @Composable () -> Unit) {
        view?.findViewById<ComposeView>(R.id.compose_view)?.setContent {
            MoonTheme(colorScheme = theme) {
                content()
            }
        }
    }
}
