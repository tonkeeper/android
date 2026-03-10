package ui.theme.modifiers

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import ui.theme.Dimens
import ui.theme.UIKit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.tan

@Composable
fun rememberShimmerPhase(
    durationMillis: Int = 1000,
    repeatDelay: Int = 0
): State<Float> {
    val t = rememberInfiniteTransition(label = "fbShimmerPhase")
    return t.animateFloat(
        initialValue = 0f,
        targetValue = 1f + repeatDelay / durationMillis.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis + repeatDelay, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
}

@Composable
fun Modifier.shimmer(
    phase: Float,
    cornerRadius: Dp = Dimens.cornerMedium,
    backgroundFill: Color = UIKit.colorScheme.background.content,
    highlightColor: Color = UIKit.colorScheme.background.contentTint,
    intensity: Float = 0.30f,
    dropOff: Float = 0.60f,
    tiltDegrees: Float = 6f
): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val r = cornerRadius.toPx()

    val tiltRad = tiltDegrees * (PI.toFloat() / 180f)
    val s = tan(tiltRad)
    val overscan = abs(s) * h
    val t0Base = -overscan
    val t1Base =  w + overscan

    val translateRange = w + abs(s) * h

    val p0 = maxOf((1f - intensity - dropOff) / 2f, 0f)
    val p1 = maxOf((1f - intensity - 0.001f) / 2f, 0f)
    val p2 = minOf((1f + intensity + 0.001f) / 2f, 1f)
    val p3 = minOf((1f + intensity + dropOff) / 2f, 1f)
    val stops = arrayOf(
        0f to backgroundFill,
        p0 to backgroundFill,
        p1 to highlightColor,
        p2 to highlightColor,
        p3 to backgroundFill,
        1f to backgroundFill
    )

    val clipPath = Path().apply {
        addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(r, r)))
    }

    onDrawWithContent {
        clipPath(clipPath) {
            val tShift = (-translateRange) + (2f * translateRange) * phase.coerceIn(0f, 1f)

            val t0 = t0Base + tShift
            val t1 = t1Base + tShift
            val start = Offset(t0, s * t0)
            val end   = Offset(t1, s * t1)

            val brush = Brush.linearGradient(
                colorStops = stops,
                start = start,
                end = end
            )

            drawRect(brush = brush, size = size)
        }
    }
}