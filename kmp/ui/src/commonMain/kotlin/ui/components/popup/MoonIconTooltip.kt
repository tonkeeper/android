package ui.components.popup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import kotlin.math.roundToInt
import ui.components.moon.MoonItemIcon
import ui.painterResource
import ui.theme.UIKit

@Composable
fun MoonIconTooltip(
    text: String,
    modifier: Modifier = Modifier,
    iconPainter: Painter = painterResource(UIKitIcon.ic_information_circle_16),
    iconColor: Color = UIKit.colorScheme.icon.secondary,
    gapBelowAnchor: Dp = 4.dp,
    tooltipModifier: Modifier = Modifier,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    var anchorBounds by remember { mutableStateOf<IntRect?>(null) }
    Box(
        modifier = modifier
            .wrapContentSize()
            .onGloballyPositioned { coords ->
                val p = coords.positionInWindow()
                val s = coords.size
                anchorBounds = IntRect(
                    p.x.roundToInt(),
                    p.y.roundToInt(),
                    (p.x + s.width).roundToInt(),
                    (p.y + s.height).roundToInt(),
                )
            },
    ) {
        MoonItemIcon(
            painter = iconPainter,
            color = iconColor,
            onClick = { expanded = true },
        )
        MoonAnchorTooltip(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            text = text,
            modifier = tooltipModifier,
            gapBelowAnchor = gapBelowAnchor,
            anchorBoundsInWindow = anchorBounds,
        )
    }
}
