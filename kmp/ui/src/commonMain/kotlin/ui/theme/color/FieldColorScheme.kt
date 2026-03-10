package ui.theme.color

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class FieldColorScheme(
    val background: Color,
    val activeBorder: Color,
    val errorBorder: Color,
    val errorBackground: Color,
)
