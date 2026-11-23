package com.tonapps.core.extensions

import android.content.Context
import android.util.TypedValue
import androidx.annotation.ColorInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.tonapps.blockchain.model.legacy.WalletColor
import com.tonapps.ui.uikit.color.R
import ui.theme.AppThemeAppearance
import ui.theme.UIKit

private class WalletColorVariants(
    @ColorInt val light: Int,
    @ColorInt val dark: Int,
)

// The stored wallet color is the Blue theme shade; the grays have their own Light and Dark shades.
private val themedWalletColors = mapOf(
    WalletColor.SteelGray to WalletColorVariants(light = 0xFF818C99.toInt(), dark = 0xFF2F2F33.toInt()),
    WalletColor.LightSteelGray to WalletColorVariants(light = 0xFF95A0AD.toInt(), dark = 0xFF4E4E52.toInt()),
    WalletColor.Gray to WalletColorVariants(light = 0xFFB6BBC2.toInt(), dark = 0xFF8D8D93.toInt()),
)

@ColorInt
fun resolveWalletColor(@ColorInt color: Int, appearance: AppThemeAppearance): Int {
    val variants = themedWalletColors[color] ?: return color
    return when (appearance) {
        AppThemeAppearance.Light -> variants.light
        AppThemeAppearance.Dark -> variants.dark
        AppThemeAppearance.Blue -> color
    }
}

@Composable
@ReadOnlyComposable
fun walletColor(@ColorInt color: Int): Color {
    return Color(resolveWalletColor(color, UIKit.colorScheme.appearance))
}

@ColorInt
fun Context.walletColor(@ColorInt color: Int): Int {
    val value = TypedValue()
    if (!theme.resolveAttribute(R.attr.themeAppearance, value, true)) {
        return color
    }
    // Values of the themeAppearance enum in ui/uikit/color attrs.xml.
    val appearance = when (value.data) {
        0 -> AppThemeAppearance.Light
        1 -> AppThemeAppearance.Dark
        2 -> AppThemeAppearance.Blue
        else -> return color
    }
    return resolveWalletColor(color, appearance)
}
