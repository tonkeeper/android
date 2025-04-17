package uikit.compose

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AppColors(
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textAccent: Color,
    val textPrimaryAlternate: Color,

    val backgroundPage: Color,
    val backgroundTransparent: Color,
    val backgroundContent: Color,
    val backgroundContentTint: Color,
    val backgroundContentAttention: Color,
    val backgroundHighlighted: Color,
    val backgroundOverlayStrong: Color,
    val backgroundOverlayLight: Color,
    val backgroundOverlayExtraLight: Color,

    val iconPrimary: Color,
    val iconSecondary: Color,
    val iconTertiary: Color,
    val iconPrimaryAlternate: Color,

    val buttonPrimaryBackground: Color,
    val buttonPrimaryBackgroundDisabled: Color,
    val buttonPrimaryBackgroundHighlighted: Color,
    val buttonPrimaryForeground: Color,

    val buttonSecondaryBackground: Color,
    val buttonSecondaryBackgroundDisabled: Color,
    val buttonSecondaryBackgroundHighlighted: Color,
    val buttonSecondaryForeground: Color,

    val buttonTertiaryBackground: Color,
    val buttonTertiaryBackgroundDisabled: Color,
    val buttonTertiaryBackgroundHighlighted: Color,
    val buttonTertiaryForeground: Color,

    val buttonGreenBackground: Color,
    val buttonGreenBackgroundDisabled: Color,
    val buttonGreenBackgroundHighlighted: Color,

    val buttonOrangeBackground: Color,
    val buttonOrangeBackgroundDisabled: Color,
    val buttonOrangeBackgroundHighlighted: Color,

    val fieldBackground: Color,
    val fieldActiveBorder: Color,
    val fieldErrorBorder: Color,
    val fieldErrorBackground: Color,

    val accentBlue: Color,
    val accentGreen: Color,
    val accentRed: Color,
    val accentOrange: Color,
    val accentPurple: Color,

    val tabBarActiveIcon: Color,
    val tabBarInactiveIcon: Color,

    val separatorCommon: Color,
    val separatorAlternate: Color,

    val isDark: Boolean
)

val LocalAppColors = staticCompositionLocalOf<AppColors> {
    error("No AppColors provided. Have you wrapped your composable in AppTheme?")
}