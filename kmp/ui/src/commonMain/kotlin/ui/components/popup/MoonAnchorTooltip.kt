package ui.components.popup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ui.components.moon.container.MoonSurface
import ui.theme.UIKit

@Composable
fun MoonAnchorTooltip(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    gapBelowAnchor: Dp = 4.dp,
    anchorBoundsInWindow: IntRect? = null,
) {
    if (!expanded) return

    var tailOnTopOfBubble by remember { mutableStateOf(true) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val maxBubbleWidth = remember(anchorBoundsInWindow, configuration.screenWidthDp, density) {
        val windowWpx = with(density) { configuration.screenWidthDp.dp.roundToPx() }
        if (anchorBoundsInWindow != null) {
            with(density) {
                computeAnchorTooltipMaxBubbleWidthPx(
                    anchorBounds = anchorBoundsInWindow,
                    windowWidthPx = windowWpx,
                    density = density,
                ).toDp()
            }
        } else {
            AnchorTooltipTailSpec.absoluteMaxBubbleWidth
        }
    }
    val positionProvider = remember(gapBelowAnchor, density) {
        AnchorTooltipPositionProvider(
            gapBelowAnchor = gapBelowAnchor,
            density = density,
            onTailOnTopOfBubble = { tailOnTopOfBubble = it },
        )
    }

    Popup(
        onDismissRequest = onDismissRequest,
        popupPositionProvider = positionProvider,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
    ) {
        MoonTooltipBubble(
            text = text,
            modifier = modifier.widthIn(max = maxBubbleWidth),
            tailOnTopOfBubble = tailOnTopOfBubble,
        )
    }
}

@Composable
fun MoonTooltipBubble(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = UIKit.colorScheme.background.contentAttention,
    textColor: Color = UIKit.colorScheme.text.primary,
    tailOnTopOfBubble: Boolean = true,
) {
    val bubbleShape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier.width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.Start,
    ) {
        if (tailOnTopOfBubble) {
            TooltipTail(pointingUp = true, backgroundColor = backgroundColor)
        }
        MoonSurface(
            shape = bubbleShape,
            color = backgroundColor,
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style = UIKit.typography.body2,
                color = textColor,
            )
        }
        if (!tailOnTopOfBubble) {
            TooltipTail(pointingUp = false, backgroundColor = backgroundColor)
        }
    }
}

@Composable
private fun TooltipTail(
    pointingUp: Boolean,
    backgroundColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AnchorTooltipTailSpec.height),
    ) {
        Canvas(
            modifier = Modifier
                .size(AnchorTooltipTailSpec.width, AnchorTooltipTailSpec.height)
                .align(Alignment.TopStart)
                .offset(x = AnchorTooltipTailSpec.insetFromStart),
        ) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                if (pointingUp) {
                    moveTo(w / 2f, 0f)
                    lineTo(0f, h)
                    lineTo(w, h)
                } else {
                    moveTo(0f, 0f)
                    lineTo(w, 0f)
                    lineTo(w / 2f, h)
                }
                close()
            }
            drawPath(path, backgroundColor)
        }
    }
}
