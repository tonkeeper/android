package ui.theme.modifiers

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.theme.Dimens
import ui.theme.UIKit

@Composable
fun Modifier.bottomDivider(
    enabled: Boolean,
    insetStart: Dp = Dimens.offsetMedium,
    thickness: Dp = .5f.dp,
    color: Color = UIKit.colorScheme.separator.common,
): Modifier {
    if (!enabled) {
        return this
    }
    return this.drawWithCache {
        val t = thickness.toPx()
        val startX = insetStart.toPx()
        onDrawBehind {
            val y = size.height - t / 2f
            drawLine(
                color = color,
                start = Offset(startX, y),
                end = Offset(size.width, y),
                strokeWidth = t
            )
        }
    }
}
