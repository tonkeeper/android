package ui.theme.color

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class BackgroundColorScheme(
    val page: Color,
    val transparent: Color,
    val content: Color,
    val contentTint: Color,
    val contentAttention: Color,
    val highlighted: Color,
    val overlayStrong: Color,
    val overlayLight: Color,
    val overlayExtraLight: Color
)