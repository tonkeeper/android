package ui.components.moon.container

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import ui.theme.UIKit

@Composable
@NonRestartableComposable
fun MoonSurface(
    modifier: Modifier = Modifier,
    shape: Shape = UIKit.shapes.extraLarge,
    color: Color = UIKit.colorScheme.background.page,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.clip(shape),
        color = color,
    ) {
        content()
    }
}