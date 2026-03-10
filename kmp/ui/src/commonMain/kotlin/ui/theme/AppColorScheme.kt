package ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.TopAppBarColors
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import ui.theme.color.AccentColorScheme
import ui.theme.color.BackgroundColorScheme
import ui.theme.color.ButtonColorScheme
import ui.theme.color.FieldColorScheme
import ui.theme.color.IconColorScheme
import ui.theme.color.SeparatorColorScheme
import ui.theme.color.TabBarColorScheme
import ui.theme.color.TextColorScheme

@OptIn(ExperimentalMaterial3Api::class)
@Immutable
data class AppColorScheme(
    val text: TextColorScheme,
    val background: BackgroundColorScheme,
    val icon: IconColorScheme,
    val buttonPrimary: ButtonColorScheme,
    val buttonSecondary: ButtonColorScheme,
    val buttonTertiary: ButtonColorScheme,
    val buttonGreen: ButtonColorScheme,
    val buttonOrange: ButtonColorScheme,
    val field: FieldColorScheme,
    val accent: AccentColorScheme,
    val tabBar: TabBarColorScheme,
    val separator: SeparatorColorScheme
) {

    val rippleConfiguration = RippleConfiguration(
        color = background.highlighted
    )

    val shimmerColors = listOf(
        background.content,
        icon.secondary.copy(alpha = 0.3f),
        background.content,
    )

    val topAppBarColors = TopAppBarColors(
        containerColor = background.page,
        scrolledContainerColor = background.page,
        navigationIconContentColor = icon.primary,
        titleContentColor = text.primary,
        actionIconContentColor = icon.primary
    )
}

val LocalAppColorScheme = staticCompositionLocalOf<AppColorScheme> {
    error("No AppColorScheme provided")
}