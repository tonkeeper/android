package ui.components.popup

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelColors
import ui.components.moon.container.MoonSurface
import ui.theme.UIKit

private const val MoonIconTooltipAnimMs = 150
private const val MoonIconTooltipHiddenScale = 0.3f

@Composable
fun MoonAnchorTooltip(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    gapBelowAnchor: Dp = 4.dp,
    anchorBoundsInWindow: IntRect? = null,
    badge: String? = null,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = UIKit.colorScheme.background.contentAttention,
    textColor: Color = UIKit.colorScheme.text.primary,
    maxWidth: Dp = 200.dp,
    preferAbove: Boolean = false,
    centerTail: Boolean = false,
) {
    val expandedState = remember { MutableTransitionState(false) }
    expandedState.targetState = expanded
    if (!expandedState.currentState && !expandedState.targetState) {
        return
    }

    var tailOnTop by remember { mutableStateOf(!preferAbove) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val maxTooltipWidth = remember(anchorBoundsInWindow, configuration.screenWidthDp, density) {
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
    val positionProvider = remember(gapBelowAnchor, density, preferAbove, centerTail) {
        AnchorTooltipPositionProvider(
            gapBelowAnchor = gapBelowAnchor,
            density = density,
            preferAbove = preferAbove,
            centerTail = centerTail,
            onTailOnTopOfBubble = { tailOnTop = it },
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
        val transition = rememberTransition(expandedState, label = "MoonAnchorTooltip")
        val scale by transition.animateFloat(
            transitionSpec = {
                if (false isTransitioningTo true) {
                    tween(MoonIconTooltipAnimMs, easing = LinearOutSlowInEasing)
                } else {
                    tween(MoonIconTooltipAnimMs, easing = FastOutLinearInEasing)
                }
            },
            label = "scale",
        ) {
            if (it) {
                1f
            } else {
                MoonIconTooltipHiddenScale
            }
        }
        val alpha by transition.animateFloat(
            transitionSpec = {
                if (false isTransitioningTo true) {
                    tween(MoonIconTooltipAnimMs, easing = LinearOutSlowInEasing)
                } else {
                    tween(MoonIconTooltipAnimMs, easing = FastOutLinearInEasing)
                }
            },
            label = "alpha",
        ) {
            if (it) {
                1f
            } else {
                0f
            }
        }

        var tooltipTransformOrigin by remember {
            mutableStateOf(TransformOrigin(0.14f, 0f))
        }
        MoonTooltipContent(
            text = text,
            modifier = modifier
                .widthIn(max = maxTooltipWidth.coerceAtMost(maxWidth))
                .onSizeChanged { size ->
                    if (size.width <= 0) {
                        return@onSizeChanged
                    }
                    val pivotX = if (centerTail) {
                        0.5f
                    } else {
                        val tailCenterPx = with(density) {
                            (AnchorTooltipTailSpec.insetFromStart + AnchorTooltipTailSpec.width / 2).roundToPx()
                        }
                        (tailCenterPx / size.width.toFloat()).coerceIn(0.05f, 0.95f)
                    }
                    val pivotY = if (tailOnTop) {
                        0f
                    } else {
                        1f
                    }
                    tooltipTransformOrigin = TransformOrigin(pivotX, pivotY)
                }
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = tooltipTransformOrigin
                },
            tailOnTop = tailOnTop,
            badge = badge,
            onClick = onClick,
            backgroundColor = backgroundColor,
            textColor = textColor,
            centerTail = centerTail,
        )
    }
}

@Composable
private fun MoonTooltipContent(
    text: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = UIKit.colorScheme.background.contentAttention,
    textColor: Color = UIKit.colorScheme.text.primary,
    tailOnTop: Boolean = true,
    badge: String? = null,
    onClick: (() -> Unit)? = null,
    centerTail: Boolean = false,
) {
    val surfaceShape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier.width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.Start,
    ) {
        if (tailOnTop) {
            TooltipTail(
                pointingUp = true,
                backgroundColor = backgroundColor,
                centerTail = centerTail,
            )
        }
        MoonSurface(
            shape = surfaceShape,
            color = backgroundColor,
        ) {
            Row(
                modifier = Modifier
                    .then(
                        if (onClick != null) {
                            Modifier.clickable(onClick = onClick)
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (badge != null) {
                    MoonLabel(
                        text = badge,
                        colors = MoonLabelColors(
                            textColor = backgroundColor,
                            backgroundColor = Color.White,
                        ),
                    )
                }
                Text(
                    text = text,
                    style = UIKit.typography.body2,
                    color = textColor,
                )
            }
        }
        if (!tailOnTop) {
            TooltipTail(
                pointingUp = false,
                backgroundColor = backgroundColor,
                centerTail = centerTail,
            )
        }
    }
}

@Composable
private fun TooltipTail(
    pointingUp: Boolean,
    backgroundColor: Color,
    centerTail: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(AnchorTooltipTailSpec.height),
    ) {
        Canvas(
            modifier = Modifier
                .size(AnchorTooltipTailSpec.width, AnchorTooltipTailSpec.height)
                .then(
                    if (centerTail) {
                        Modifier.align(Alignment.TopCenter)
                    } else {
                        Modifier
                            .align(Alignment.TopStart)
                            .offset(x = AnchorTooltipTailSpec.insetFromStart)
                    },
                ),
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
