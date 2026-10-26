package ui.theme.modifiers

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay
import ui.theme.UIKit
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.tan
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@Composable
fun Modifier.textShimmer(
    isActive: Boolean,
    dimColor: Color = UIKit.colorScheme.background.page,
    appearsAfter: Duration = TextShimmerDelay,
): Modifier {
    var shownSince by remember { mutableStateOf<TimeMark?>(null) }

    LaunchedEffect(isActive) {
        val since = shownSince
        if (isActive) {
            if (since == null) {
                delay(appearsAfter)
                shownSince = TimeSource.Monotonic.markNow()
            }
        } else if (since != null) {
            val owed = TextShimmerPass - since.elapsedNow()
            if (owed.isPositive()) {
                delay(owed)
            }
            shownSince = null
        }
    }

    val shown = shownSince != null
    val fade = animateFloatAsState(
        targetValue = if (shown) {
            1f
        } else {
            0f
        },
        animationSpec = tween(durationMillis = TEXT_SHIMMER_FADE_MS, easing = LinearOutSlowInEasing),
        label = "textShimmerFade",
    )
    val sweeping by remember { derivedStateOf { shownSince != null || fade.value > 0f } }
    val phase = remember { Animatable(0f) }

    LaunchedEffect(sweeping) {
        if (!sweeping) {
            phase.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            phase.snapTo(0f)
            phase.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = TextShimmerPass.inWholeMilliseconds.toInt(),
                    easing = TextShimmerPassEasing,
                ),
            )
            delay(TextShimmerPause)
        }
    }

    val highlight = if (UIKit.isLightTheme) {
        TextShimmerHighlightLight
    } else {
        TextShimmerHighlightDark
    }
    val mirrored = LocalLayoutDirection.current == LayoutDirection.Rtl

    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val width = max(size.width, 1f)
            val bandWidth = width * TEXT_SHIMMER_BAND_WIDTH_FACTOR
            val step = bandWidth / (1f + TextShimmerSkew * TextShimmerSkew)
            val brush = Brush.linearGradient(
                colorStops = TextShimmerBellAlphas.mapIndexed { index, bellAlpha ->
                    index / (TextShimmerBellAlphas.size - 1f) to highlight.copy(alpha = bellAlpha)
                }.toTypedArray(),
                start = Offset.Zero,
                end = Offset(step, step * TextShimmerSkew),
            )
            val direction = if (mirrored) {
                -1f
            } else {
                1f
            }
            onDrawWithContent {
                drawContent()
                val alpha = fade.value
                if (alpha <= 0f) {
                    return@onDrawWithContent
                }
                drawRect(color = dimColor, alpha = TEXT_SHIMMER_DIM_OPACITY * alpha, blendMode = BlendMode.SrcAtop)
                val bandStart = -bandWidth + (width + bandWidth) * phase.value
                scale(scaleX = direction, scaleY = 1f) {
                    translate(left = bandStart) {
                        drawRect(
                            brush = brush,
                            topLeft = Offset(-bandStart, 0f),
                            size = size,
                            alpha = alpha,
                            blendMode = BlendMode.SrcAtop,
                        )
                    }
                }
            }
        }
}

private val TextShimmerDelay = 300.milliseconds
private val TextShimmerPass = 1400.milliseconds
private val TextShimmerPause = 200.milliseconds
private const val TEXT_SHIMMER_FADE_MS = 250
private const val TEXT_SHIMMER_DIM_OPACITY = 0.6f
private const val TEXT_SHIMMER_BAND_WIDTH_FACTOR = 0.7f
private const val TEXT_SHIMMER_SKEW_DEGREES = 10.0

private val TextShimmerPassEasing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private val TextShimmerHighlightDark = Color(0xFFC2DAFF)
private val TextShimmerHighlightLight = Color(0xFF1F5FBF)
private val TextShimmerBellAlphas = listOf(0f, 0.08f, 0.38f, 0.98f, 0.38f, 0.08f, 0f)
private val TextShimmerSkew = tan(TEXT_SHIMMER_SKEW_DEGREES * PI / 180.0).toFloat()
