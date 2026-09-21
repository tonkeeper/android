package ui.components.moon.cell

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.components.moon.ButtonColorsPrimary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonLoader
import ui.painterResource
import ui.theme.UIKit
import ui.theme.modifiers.modifyIf

private val ItemHeight = 56.dp
private val ItemWidth = 76.dp
private const val PhaseDurationMillis = 2000L
private const val FadeDurationMillis = 400

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
    subtitle: String? = null,
    sliderState: SliderState = rememberSliderState(),
    error: String? = null,
    buttonTitle: String,
    enabled: Boolean = true,
    thumbColor: Color = UIKit.colorScheme.buttonPrimary.primaryBackground,
    loaderColor: Color = UIKit.colorScheme.icon.secondary,
    onConfirm: () -> Unit,
    onClick: (() -> Unit)? = null,
    onDisabledClick: (() -> Unit)? = null,
    onDone: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(16.dp)
            .height(ItemHeight)
            .then(modifier),
    ) {
        if (error != null) {
            Error(
                error = error,
                buttonTitle = buttonTitle,
                onClick = onClick,
            )
        } else {
            when (state) {
                MoonSlideConfirmationState.Slider -> Slider(
                    title = title,
                    subtitle = subtitle,
                    sliderState = sliderState,
                    enabled = enabled,
                    thumbColor = thumbColor,
                    onConfirm = onConfirm,
                    onDisabledClick = onDisabledClick,
                )
                MoonSlideConfirmationState.Loader -> Loader(
                    title = title,
                    loaderColor = loaderColor,
                )
                MoonSlideConfirmationState.Done -> Done(title, onDone)
            }
        }
    }
}

@Composable
private fun Loader(
    title: String,
    loaderColor: Color,
) {
    Column(
        modifier = Modifier
            .height(ItemWidth)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        MoonLoader(
            modifier = Modifier.size(24.dp),
            color = loaderColor,
        )
        MoonItemSubtitle(
            text = title,
            color = UIKit.colorScheme.text.secondary,
        )
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
private fun Error(
    error: String,
    buttonTitle: String,
    onClick: (() -> Unit)?,
) {
    // Keyed on the error value so a repeated failure restarts the message phase even when an
    // intermediate null was conflated away by the state flow.
    var showButton by remember(error) { mutableStateOf(false) }

    if (onClick != null) {
        LaunchedEffect(error) {
            delay(PhaseDurationMillis)
            showButton = true
        }
    }

    Crossfade(
        targetState = showButton,
        animationSpec = tween(durationMillis = FadeDurationMillis),
        label = "errorPhase",
    ) { button ->
        if (button && onClick != null) {
            ActionButton(
                title = buttonTitle,
                onClick = onClick,
            )
        } else {
            ErrorMessage(text = error)
        }
    }
}

@Composable
private fun ErrorMessage(text: String) {
    var animateIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        animateIn = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = tween(durationMillis = FadeDurationMillis),
        label = "errorAlpha",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .graphicsLayer {
                this.alpha = alpha
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        // Centered so a larger font scale squeezes the content symmetrically instead of clipping
        // the text against the bottom of the fixed-height parent.
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        MoonItemIcon(
            painter = painterResource(UIKitIcon.ic_exclamationmark_circle_32),
            color = UIKit.colorScheme.accent.red,
            size = 32.dp,
        )

        Text(
            text = text,
            style = UIKit.typography.label2,
            color = UIKit.colorScheme.accent.red,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ActionButton(
    title: String,
    onClick: () -> Unit,
) {
    MoonAccentButton(
        modifier = Modifier.fillMaxWidth(),
        text = title,
        size = ButtonSizeLarge,
        buttonColors = ButtonColorsPrimary,
        icon = {
            Icon(
                painter = painterResource(UIKitIcon.ic_refresh_16),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        onClick = onClick,
    )
}

@Composable
private fun Slider(
    title: String,
    subtitle: String?,
    sliderState: SliderState,
    enabled: Boolean = true,
    thumbColor: Color = UIKit.colorScheme.buttonPrimary.primaryBackground,
    onConfirm: () -> Unit,
    onDisabledClick: (() -> Unit)? = null,
) {
    val colors = UIKit.colorScheme
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val offsetX = sliderState.offsetX
    // offsetX is always a positive 0..maxDragPx travel distance; the sign converts it to raw
    // pixels, since translationX and pointer deltas are not mirrored by the layout direction.
    val dragSign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f

    val contentAlpha = if (enabled) 1f else 0.2f
    val disabledClick = onDisabledClick.takeIf { !enabled }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .semantics(mergeDescendants = false) {
                testTagsAsResourceId = true
                testTag = "slide_confirm_track"
            }
            .clip(UIKit.shapes.large)
            .background(colors.background.content)
            .modifyIf {
                disabledClick?.let {
                    clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = it,
                    )
                }
            },
    ) {
        val thumbWidthPx = with(LocalDensity.current) { ItemWidth.toPx() }
        val maxDragPx = constraints.maxWidth - thumbWidthPx
        sliderState.maxDragPx = maxDragPx

        val progress = if (maxDragPx > 0f) {
            (offsetX.value / maxDragPx).coerceIn(0f, 1f)
        } else {
            0f
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    alpha = contentAlpha * (1f - progress)
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ShimmerLabel(
                text = title,
                animate = enabled && !sliderState.isConfirmed,
                style = if (subtitle.isNullOrBlank()) {
                    UIKit.typography.label1
                } else {
                    UIKit.typography.label2
                },
                color = colors.text.secondary,
            )

            if (!subtitle.isNullOrBlank()) {
                ShimmerLabel(
                    text = subtitle,
                    animate = enabled && !sliderState.isConfirmed,
                    style = UIKit.typography.body3,
                    color = colors.text.tertiary,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .semantics(mergeDescendants = false) {
                    testTagsAsResourceId = true
                    testTag = "slide_confirm_thumb"
                }
                .graphicsLayer {
                    translationX = offsetX.value * dragSign
                    alpha = contentAlpha
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
                                    (offsetX.value + dragAmount * dragSign).coerceIn(0f, maxDragPx)
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
    style: TextStyle = UIKit.typography.label1,
    color: Color = UIKit.colorScheme.text.tertiary,
) {
    val highlightColor = UIKit.colorScheme.accent.blue.copy(alpha = 0.5f)
    val gradientColors = remember(color, highlightColor) {
        listOf(color, highlightColor, highlightColor, color)
    }
    val gradientWidthPx = with(LocalDensity.current) { 168.dp.toPx() }

    if (!animate) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = 1,
            modifier = modifier,
        )
        return
    }

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
        style = style,
        color = color,
        maxLines = 1,
        modifier = modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val progress = progressState.value
                val sweep = size.width + gradientWidthPx
                val offset = if (layoutDirection == LayoutDirection.Rtl) {
                    size.width - sweep * progress
                } else {
                    sweep * progress - gradientWidthPx
                }
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
