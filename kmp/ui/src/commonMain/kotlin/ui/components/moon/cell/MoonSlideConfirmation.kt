package ui.components.moon.cell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.painterResource
import ui.theme.UIKit

private val ItemHeight = 56.dp
private val ItemWidth = 76.dp

enum class MoonSlideConfirmationState {
    Slider, Loader, Done
}

@Stable
class SliderState internal constructor() {
    internal val offsetX = Animatable(0f)
    internal var maxDragPx = 0f

    val isConfirmed: Boolean
        get() = maxDragPx > 0f && offsetX.value >= maxDragPx

    suspend fun reset() {
        offsetX.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            ),
        )
    }
}

@Composable
fun rememberSliderState(): SliderState {
    return remember { SliderState() }
}

@Composable
fun MoonSlideConfirmation(
    state: MoonSlideConfirmationState,
    title: String,
    modifier: Modifier = Modifier,
    sliderState: SliderState = rememberSliderState(),
    error: String? = null,
    enabled: Boolean = true,
    thumbColor: Color = UIKit.colorScheme.buttonPrimary.primaryBackground,
    onConfirm: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onDone: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(16.dp)
            .height(ItemHeight)
            .then(modifier)
    ) {
        if (error != null) {
            MoonErrorCell(
                text = error,
                height = ItemWidth,
                modifier = if (onRetry != null) Modifier.clickable { onRetry() } else Modifier,
            )
        } else {
            when (state) {
                MoonSlideConfirmationState.Slider -> Slider(
                    title = title,
                    sliderState = sliderState,
                    enabled = enabled,
                    thumbColor = thumbColor,
                    onConfirm = onConfirm,
                )
                MoonSlideConfirmationState.Loader -> MoonLoaderCell(height = ItemWidth)
                MoonSlideConfirmationState.Done -> Done(title, onDone)
            }
        }
    }
}

@Composable
private fun Done(
    title: String,
    onDone: () -> Unit,
) {
    var animateIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        animateIn = true
        delay(2000)
        onDone()
    }

    val alpha by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "doneAlpha",
    )

    Column(
        modifier = Modifier
            .height(ItemWidth)
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MoonItemIcon(
            painter = painterResource(UIKitIcon.ic_checkmark_circle_32),
            color = UIKit.colorScheme.accent.green,
            size = 32.dp,
        )

        MoonItemSubtitle(
            text = title,
            color = UIKit.colorScheme.accent.green,
        )
    }
}

@Composable
private fun Slider(
    title: String,
    sliderState: SliderState,
    enabled: Boolean = true,
    thumbColor: Color = UIKit.colorScheme.buttonPrimary.primaryBackground,
    onConfirm: () -> Unit,
) {
    val colors = UIKit.colorScheme
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val offsetX = sliderState.offsetX

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clip(UIKit.shapes.large)
            .background(colors.background.content)
            .alpha(if (enabled) 1f else 0.2f),
    ) {
        val thumbWidthPx = with(LocalDensity.current) { ItemWidth.toPx() }
        val maxDragPx = constraints.maxWidth - thumbWidthPx
        sliderState.maxDragPx = maxDragPx

        val progress = if (maxDragPx > 0f) {
            (offsetX.value / maxDragPx).coerceIn(0f, 1f)
        } else {
            0f
        }

        ShimmerLabel(
            text = title,
            animate = enabled && !sliderState.isConfirmed,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { alpha = 1f - progress },
        )

        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = offsetX.value
                }
                .width(ItemWidth)
                .fillMaxHeight()
                .clip(UIKit.shapes.large)
                .background(thumbColor)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetX.value >= maxDragPx) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onConfirm()
                                } else {
                                    sliderState.reset()
                                }
                            }
                        },

                        onDragCancel = {
                            scope.launch { sliderState.reset() }
                        },

                        onHorizontalDrag = { change, dragAmount ->
                            if (sliderState.isConfirmed) return@detectHorizontalDragGestures
                            change.consume()
                            scope.launch {
                                val newValue =
                                    (offsetX.value + dragAmount).coerceIn(0f, maxDragPx)
                                offsetX.snapTo(newValue)
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            val iconRes = if (progress >= 1f) {
                UIKitIcon.ic_donemark_otline_28
            } else {
                UIKitIcon.ic_arrow_right_outline_28
            }
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = colors.buttonPrimary.primaryForeground,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun ShimmerLabel(
    text: String,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = UIKit.colorScheme
    val textColor = colors.text.tertiary

    if (!animate) {
        Text(
            text = text,
            style = UIKit.typography.label1,
            color = textColor,
            maxLines = 1,
            modifier = modifier,
        )
        return
    }

    val highlightColor = colors.accent.blue.copy(alpha = 0.5f)
    val gradientColors = remember(textColor, highlightColor) {
        listOf(textColor, highlightColor, highlightColor, textColor)
    }
    val gradientWidthPx = with(LocalDensity.current) { 168.dp.toPx() }

    val transition = rememberInfiniteTransition(label = "slideShimmer")

    val progressState = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )

    Text(
        text = text,
        style = UIKit.typography.label1,
        color = textColor,
        maxLines = 1,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val progress = progressState.value
                val sweep = size.width + gradientWidthPx
                val offset = sweep * progress - gradientWidthPx
                drawRect(
                    brush = Brush.linearGradient(
                        colors = gradientColors,
                        start = Offset(offset, 0f),
                        end = Offset(offset + gradientWidthPx, 0f),
                    ),
                    blendMode = BlendMode.SrcIn,
                )
            },
    )
}
