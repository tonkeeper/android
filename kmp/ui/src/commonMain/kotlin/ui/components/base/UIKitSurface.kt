package ui.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import ui.theme.Shapes
import ui.theme.UIKit

@Composable
@NonRestartableComposable
fun UIKitSurface(
    modifier: Modifier = Modifier,
    shape: Shape = Shapes.medium,
    color: Color = UIKit.colorScheme.background.content,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.clip(shape).background(color)
    ) {
        content()
    }
}