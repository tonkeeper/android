package ui.theme.color

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter

@Immutable
data class IconColorScheme(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val primaryAlternate: Color,
) {

    val secondaryColorFilter = ColorFilter.tint(
        color = secondary,
        blendMode = BlendMode.SrcIn
    )
}