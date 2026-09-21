package ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ui.theme.color.AccentColorScheme
import ui.theme.color.BackgroundColorScheme
import ui.theme.color.ButtonColorScheme
import ui.theme.color.FieldColorScheme
import ui.theme.color.IconColorScheme
import ui.theme.color.SeparatorColorScheme
import ui.theme.color.TabBarColorScheme
import ui.theme.color.TextColorScheme

@Composable
fun appColorSchemeBlue() = AppColorScheme(
    appearance = AppThemeAppearance.Blue,
    text = TextColorScheme(
        primary = Color(0xFFFFFFFF),
        secondary = Color(0xFF8994A3),
        tertiary = Color(0xFF556170),
        accent = Color(0xFF3C95FA),
        primaryAlternate = Color(0xFF10161F)
    ),
    background = BackgroundColorScheme(
        page = Color(0xFF10161F),
        pageAlternate = Color(0xFF10161F),
        transparent = Color(0xF510161F),
        content = Color(0xFF1D2633),
        contentAlternate = Color(0xFF1D2633),
        contentTint = Color(0xFF2E3847),
        contentAttention = Color(0xFF384457),
        highlighted = Color(0xFFC2DAFF),
        overlayStrong = Color(0xB8000000),
        overlayLight = Color(0x7A000000),
        overlayExtraLight = Color(0x3D000000)
    ),
    icon = IconColorScheme(
        primary = Color(0xFFFFFFFF),
        secondary = Color(0xFF8994A3),
        tertiary = Color(0xFF556170),
        primaryAlternate = Color(0xFF10161F)
    ),
    buttonPrimary = ButtonColorScheme(
        primaryBackground = Color(0xFF3C95FA),
        primaryBackgroundDisable = Color(0xFF387BC7),
        primaryBackgroundHighlighted = Color(0xFF55A2FA),
        primaryForeground = Color(0xFFFFFFFF)
    ),
    buttonSecondary = ButtonColorScheme(
        primaryBackground = Color(0xFF1D2633),
        primaryBackgroundDisable = Color(0xFF171F29),
        primaryBackgroundHighlighted = Color(0xFF222C3B),
        primaryForeground = Color(0xFFFFFFFF)
    ),
    buttonTertiary = ButtonColorScheme(
        primaryBackground = Color(0xFF2e3846),
        primaryBackgroundDisable = Color(0xFF28303D),
        primaryBackgroundHighlighted = Color(0xFF364052),
        primaryForeground = Color(0xFFFFFFFF)
    ),
    buttonGreen = ButtonColorScheme(
        primaryBackground = Color(0xFF39CC83),
        primaryBackgroundDisable = Color(0xFF2B9962),
        primaryBackgroundHighlighted = Color(0xFF49CC8B),
        primaryForeground = Color(0xFFFFFFFF)
    ),
    buttonOrange = ButtonColorScheme(
        primaryBackground = Color(0xFFF5A73B),
        primaryBackgroundDisable = Color(0xFFD68B2F),
        primaryBackgroundHighlighted = Color(0xFFFFC25E),
        primaryForeground = Color(0xFFFFFFFF)
    ),
    buttonRed = ButtonColorScheme(
        primaryBackground = Color(0xFFFF4766),
        primaryBackgroundDisable = Color(0xFFC2364E),
        primaryBackgroundHighlighted = Color(0xFFFF5E79),
        primaryForeground = Color(0xFFFFFFFF)
    ),
    field = FieldColorScheme(
        background = Color(0xFF1D2633),
        activeBorder = Color(0xFF3C95FA),
        errorBorder = Color(0xFFFF4766),
        errorBackground = Color(0xFF241A25)
    ),
    accent = AccentColorScheme(
        blue = Color(0xFF3C95FA),
        green = Color(0xFF39CC83),
        red = Color(0xFFFF4766),
        orange = Color(0xFFF5A73B),
        purple = Color(0xFF7665E5)
    ),
    tabBar = TabBarColorScheme(
        activeIcon = Color(0xFF3C95FA),
        inactiveIcon = Color(0xFF8994A3)
    ),
    separator = SeparatorColorScheme(
        common = Color(0x14C2DAFF),
        alternate = Color(0x14FFFFFF)
    )
)