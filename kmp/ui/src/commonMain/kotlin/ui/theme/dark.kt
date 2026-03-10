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

fun appColorSchemeDark() = AppColorScheme(
    text = TextColorScheme(
        primary = Color(0xFFD9D9D9),
        secondary = Color(0xFF8D8D93),
        tertiary = Color(0xFF4E4E52),
        accent = Color(0xFF45AEF5),
        primaryAlternate = Color(0xFF000000)
    ),
    background = BackgroundColorScheme(
        page = Color(0xFF000000),
        transparent = Color(0xF5000000),
        content = Color(0xFF17171A),
        contentTint = Color(0xFF222224),
        contentAttention = Color(0xFF2F2F33),
        highlighted = Color(0x0AFFFFFF),
        overlayStrong = Color(0xB80F0F0F),
        overlayLight = Color(0x7A0F0F0F),
        overlayExtraLight = Color(0x3D0F0F0F)
    ),
    icon = IconColorScheme(
        primary = Color(0xFFFFFFFF),
        secondary = Color(0xFF8D8D93),
        tertiary = Color(0xFF4E4E52),
        primaryAlternate = Color(0xFF000000)
    ),
    buttonPrimary = ButtonColorScheme(
        primaryBackground = Color(0xFF45AEF5),
        primaryBackgroundDisable = Color(0xFF378AC3),
        primaryBackgroundHighlighted = Color(0xFF5BB8F6),
        primaryForeground = Color(0xFFFFFFFF)
    ),
    buttonSecondary = ButtonColorScheme(
        primaryBackground = Color(0xFF17171A),
        primaryBackgroundDisable = Color(0xFF0E0E0F),
        primaryBackgroundHighlighted = Color(0xFF202024),
        primaryForeground = Color(0xFFFFFFFF)
    ),
    buttonTertiary = ButtonColorScheme(
        primaryBackground = Color(0xFF222224),
        primaryBackgroundDisable = Color(0xFF18181A),
        primaryBackgroundHighlighted = Color(0xFF2A2A2E),
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
    field = FieldColorScheme(
        background = Color(0xFF17171A),
        activeBorder = Color(0xFF45AEF5),
        errorBorder = Color(0xFFFF4766),
        errorBackground = Color(0x14FF4766)
    ),
    accent = AccentColorScheme(
        blue = Color(0xFF45AEF5),
        green = Color(0xFF39CC83),
        red = Color(0xFFFF4766),
        orange = Color(0xFFF5A73B),
        purple = Color(0xFF7665E5)
    ),
    tabBar = TabBarColorScheme(
        activeIcon = Color(0xFF45AEF5),
        inactiveIcon = Color(0xFF8D8D93)
    ),
    separator = SeparatorColorScheme(
        common = Color(0x14FFFFFF),
        alternate = Color(0x14FFFFFF)
    )
)
