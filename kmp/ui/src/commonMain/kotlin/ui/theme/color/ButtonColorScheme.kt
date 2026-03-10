package ui.theme.color

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class ButtonColorScheme(
    val primaryBackground: Color,
    val primaryBackgroundDisable: Color,
    val primaryBackgroundHighlighted: Color,
    val primaryForeground: Color,
)