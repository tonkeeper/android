package ui.theme.color

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class TextColorScheme(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val accent: Color,
    val primaryAlternate: Color
)
