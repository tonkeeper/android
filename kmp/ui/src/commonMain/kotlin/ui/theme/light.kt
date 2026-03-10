package ui.theme

import androidx.compose.ui.graphics.Color
import ui.theme.color.AccentColorScheme
import ui.theme.color.BackgroundColorScheme
import ui.theme.color.ButtonColorScheme
import ui.theme.color.FieldColorScheme
import ui.theme.color.IconColorScheme
import ui.theme.color.SeparatorColorScheme
import ui.theme.color.TabBarColorScheme
import ui.theme.color.TextColorScheme

fun appColorSchemeLight() = AppColorScheme(
    text = TextColorScheme(
        primary = Color(0xFF000000),
        secondary = Color(0xFF818C99),
        tertiary = Color(0xFF95A0AD),
        accent = Color(0xFF007AFF),
        primaryAlternate = Color(0xFFFFFFFF)
    ),
    background = BackgroundColorScheme(
        page = Color(0xFFEFEEF3),
        transparent = Color(0xF5FFFFFF),
        content = Color(0xFFFFFFFF),
        contentTint = Color(0xFFE7E6EB),
        contentAttention = Color(0xFFF0F0F0),
        highlighted = Color(0x14818C99),
        overlayStrong = Color(0xB8141414),
        overlayLight = Color(0x7A141414),
        overlayExtraLight = Color(0x3D141414)
    ),
    icon = IconColorScheme(
        primary = Color(0xFF000000),
        secondary = Color(0xFF818C99),
        tertiary = Color(0xFF95A0AD),
        primaryAlternate = Color(0xFFFFFFFF)
    ),
    buttonPrimary = ButtonColorScheme(
        primaryBackground = Color(0xFF007AFF),
        primaryBackgroundDisable = Color(0xFF3D9AFF),
        primaryBackgroundHighlighted = Color(0xFF1F8AFF),
        primaryForeground = Color(0xFFFFFFFF)
    ),
    buttonSecondary = ButtonColorScheme(
        primaryBackground = Color(0xFFD3D4DB),
        primaryBackgroundDisable = Color(0xFFD8D9E0),
        primaryBackgroundHighlighted = Color(0xFFC9CAD1),
        primaryForeground = Color(0xFF000000)
    ),
    buttonTertiary = ButtonColorScheme(
        primaryBackground = Color(0xFFD3D4DB),
        primaryBackgroundDisable = Color(0xFFD8D9E0),
        primaryBackgroundHighlighted = Color(0xFFC9CAD1),
        primaryForeground = Color(0xFF000000)
    ),
    buttonGreen = ButtonColorScheme(
        primaryBackground = Color(0xFF25B86F),
        primaryBackgroundDisable = Color(0xFF2B9962),
        primaryBackgroundHighlighted = Color(0xFF17C26D),
        primaryForeground = Color(0xFFFFFFFF)
    ),
    buttonOrange = ButtonColorScheme(
        primaryBackground = Color(0xFFF5A73B),
        primaryBackgroundDisable = Color(0xFFD68B2F),
        primaryBackgroundHighlighted = Color(0xFFFFC25E),
        primaryForeground = Color(0xFFFFFFFF)
    ),
    field = FieldColorScheme(
        background = Color(0x1F818C99),
        activeBorder = Color(0xFF007AFF),
        errorBorder = Color(0xFFFF3B30),
        errorBackground = Color(0x14FF3B30)
    ),
    accent = AccentColorScheme(
        blue = Color(0xFF007AFF),
        green = Color(0xFF25B86F),
        red = Color(0xFFFF3B30),
        orange = Color(0xFFF5A73B),
        purple = Color(0xFF7665E5)
    ),
    tabBar = TabBarColorScheme(
        activeIcon = Color(0xFF007AFF),
        inactiveIcon = Color(0xFF95A0AD)
    ),
    separator = SeparatorColorScheme(
        common = Color(0x1F3C3C43),
        alternate = Color(0x143C3C43)
    )
)
