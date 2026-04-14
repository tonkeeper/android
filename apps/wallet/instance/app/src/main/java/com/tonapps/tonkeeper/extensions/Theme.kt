package com.tonapps.tonkeeper.extensions

import androidx.compose.runtime.Composable
import com.tonapps.wallet.data.core.Theme
import ui.theme.AppColorScheme

@Composable
fun Theme.compose(): AppColorScheme {
    return when (key) {
        "dark" -> ui.theme.appColorSchemeDark()
        "light" -> ui.theme.appColorSchemeLight()
        else -> ui.theme.appColorSchemeBlue()
    }
}