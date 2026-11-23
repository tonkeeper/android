package com.tonapps.perps.screens.depositstatus

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ui.preview.ThemedPreview
import ui.theme.UIKit

internal const val PERPS_STEPS_LOADER_SUCCESS = 3

private const val SEGMENT_START_DEGREES = -70.9f
private const val SEGMENT_SWEEP_DEGREES = 81.8f
private const val SEGMENT_PITCH_DEGREES = 120f
private const val TRACK_ALPHA = 0.32f
private const val STATIC_HEAD = 0.5f
private const val STEPS_LOADER_UNITS = 16f
private const val LOOP_MS = 1_600
private const val HEAD_MS = 950f
private const val TAIL_START_MS = 700f
private const val TAIL_MS = 900f
private const val FILL_MS = 350
private const val RING_FADE_MS = 250
private const val CHECK_MS = 330
private const val CHECK_DELAY_MS = 120L
private const val RING_END_SCALE = 0.6f
private const val SUCCESS_DAMPING = 0.5f
private const val SPINNER_SWEEP_DEGREES = 90f
private const val SPINNER_START_DEGREES = 150f
private const val SPINNER_TURN_MS = 1_000
private const val FULL_TURN_DEGREES = 360f

private val loopEasing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
private val stepsLoaderSize = 16.dp
private val stepsStroke = 2.dp
private val stepsRadius = 6.dp
private val successRadius = 7.dp
private val checkStroke = 1.8.dp

@Composable
internal fun PerpsDepositStepsLoader(
    step: Int,
    modifier: Modifier = Modifier,
) {
    val motion = !rememberAnimationsDisabled()
    var shownStep by remember { mutableIntStateOf(step) }
    var fillingFrom by remember { mutableIntStateOf(step) }
    val fill = remember { Animatable(1f) }
    val ring = remember { Animatable((step != PERPS_STEPS_LOADER_SUCCESS).toFraction()) }
    val circle = remember { Animatable((step == PERPS_STEPS_LOADER_SUCCESS).toFraction()) }
    val check = remember { Animatable((step == PERPS_STEPS_LOADER_SUCCESS).toFraction()) }

    LaunchedEffect(step) {
        if (step == shownStep) {
            return@LaunchedEffect
        }
        val forward = step > shownStep
        fillingFrom = if (forward) {
            shownStep
        } else {
            step
        }
        shownStep = step
        if (!forward || !motion) {
            fill.snapTo(1f)
            val success = step == PERPS_STEPS_LOADER_SUCCESS
            ring.snapTo((!success).toFraction())
            circle.snapTo(success.toFraction())
            check.snapTo(success.toFraction())
            return@LaunchedEffect
        }
        fill.snapTo(0f)
        fill.animateTo(1f, tween(FILL_MS, easing = loopEasing))
        if (step == PERPS_STEPS_LOADER_SUCCESS) {
            coroutineScope {
                launch { ring.animateTo(0f, tween(RING_FADE_MS, easing = loopEasing)) }
                launch {
                    circle.animateTo(1f, spring(dampingRatio = SUCCESS_DAMPING, stiffness = Spring.StiffnessMediumLow))
                }
                launch {
                    delay(CHECK_DELAY_MS)
                    check.animateTo(1f, tween(CHECK_MS, easing = loopEasing))
                }
            }
        }
    }

    val trackColor = UIKit.colorScheme.icon.primary.copy(alpha = TRACK_ALPHA)
    val fillColor = UIKit.colorScheme.icon.primary
    val successColor = UIKit.colorScheme.accent.green

    key(shownStep) {
        val loop = if (motion && shownStep < PERPS_STEPS_LOADER_SUCCESS) {
            val transition = rememberInfiniteTransition(label = "PerpsDepositStepsLoop")
            transition.animateFloat(
                initialValue = 0f,
                targetValue = LOOP_MS.toFloat(),
                animationSpec = infiniteRepeatable(tween(LOOP_MS, easing = LinearEasing)),
                label = "PerpsDepositStepsLoopTime",
            )
        } else {
            null
        }
        Canvas(modifier = modifier.size(stepsLoaderSize)) {
            val ringAlpha = ring.value
            if (ringAlpha > 0f) {
                val ringScale = RING_END_SCALE + (1f - RING_END_SCALE) * ringAlpha
                scale(ringScale) {
                    drawSteps(
                        step = shownStep,
                        fillingFrom = fillingFrom,
                        fill = fill.value,
                        loopMs = loop?.value,
                        motion = motion,
                        trackColor = trackColor.copy(alpha = trackColor.alpha * ringAlpha),
                        fillColor = fillColor.copy(alpha = ringAlpha),
                    )
                }
            }
            if (circle.value > 0f) {
                drawSuccess(
                    circleScale = circle.value,
                    checkProgress = check.value,
                    circleColor = successColor,
                    checkColor = Color.White,
                )
            }
        }
    }
}

private fun DrawScope.drawSteps(
    step: Int,
    fillingFrom: Int,
    fill: Float,
    loopMs: Float?,
    motion: Boolean,
    trackColor: Color,
    fillColor: Color,
) {
    val stroke = stepsStroke.toPx()
    val radius = stepsRadius.toPx()
    val arcSize = Size(radius * 2, radius * 2)
    val topLeft = Offset(center.x - radius, center.y - radius)
    val capDegrees = Math.toDegrees((stroke / 2f / radius).toDouble()).toFloat()
    val roundStroke = Stroke(width = stroke, cap = StrokeCap.Round)
    for (segment in 0 until PERPS_STEPS_LOADER_SUCCESS) {
        val start = SEGMENT_START_DEGREES + segment * SEGMENT_PITCH_DEGREES
        val completed = segment < fillingFrom || (segment < step && fill >= 1f)
        if (completed) {
            drawArc(fillColor, start, SEGMENT_SWEEP_DEGREES, false, topLeft, arcSize, style = roundStroke)
            continue
        }
        drawArc(trackColor, start, SEGMENT_SWEEP_DEGREES, false, topLeft, arcSize, style = roundStroke)
        val rangeStart = start - capDegrees
        val rangeLength = SEGMENT_SWEEP_DEGREES + capDegrees * 2
        val (tail, head) = when {
            segment < step -> 0f to fill
            segment != step || fill < 1f -> continue
            loopMs != null -> loopTail(loopMs) to loopHead(loopMs)
            !motion -> 0f to STATIC_HEAD
            else -> continue
        }
        if (head <= tail) {
            continue
        }
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), Paint())
            drawArc(
                color = fillColor,
                startAngle = rangeStart + rangeLength * tail,
                sweepAngle = rangeLength * (head - tail),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = Color.Black,
                startAngle = start,
                sweepAngle = SEGMENT_SWEEP_DEGREES,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = roundStroke,
                blendMode = BlendMode.DstIn,
            )
            canvas.restore()
        }
    }
}

private fun loopHead(loopMs: Float): Float = loopEasing.transform((loopMs / HEAD_MS).coerceIn(0f, 1f))

private fun loopTail(loopMs: Float): Float = loopEasing.transform(((loopMs - TAIL_START_MS) / TAIL_MS).coerceIn(0f, 1f))

private fun DrawScope.drawSuccess(
    circleScale: Float,
    checkProgress: Float,
    circleColor: Color,
    checkColor: Color,
) {
    scale(circleScale) {
        drawCircle(color = circleColor, radius = successRadius.toPx())
    }
    if (checkProgress <= 0f) {
        return
    }
    val unit = size.width / STEPS_LOADER_UNITS
    val check = Path().apply {
        moveTo(5.04f * unit, 8.24f * unit)
        lineTo(7.12f * unit, 10.24f * unit)
        lineTo(11.04f * unit, 6.16f * unit)
    }
    val measure = PathMeasure().apply { setPath(check, false) }
    val drawn = Path()
    measure.getSegment(0f, measure.length * checkProgress, drawn, true)
    drawPath(
        path = drawn,
        color = checkColor,
        style = Stroke(width = checkStroke.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

@Composable
internal fun PerpsDepositSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    radius: Dp = 6.dp,
    strokeWidth: Dp = 2.dp,
    color: Color = UIKit.colorScheme.icon.primary,
) {
    val motion = !rememberAnimationsDisabled()
    val rotation = if (motion) {
        val transition = rememberInfiniteTransition(label = "PerpsDepositSpinner")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = FULL_TURN_DEGREES,
            animationSpec = infiniteRepeatable(tween(SPINNER_TURN_MS, easing = LinearEasing)),
            label = "PerpsDepositSpinnerRotation",
        ).value
    } else {
        0f
    }
    Canvas(modifier = modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val ringRadius = radius.toPx()
        drawCircle(
            color = color.copy(alpha = TRACK_ALPHA),
            radius = ringRadius,
            style = Stroke(width = stroke),
        )
        rotate(rotation) {
            drawArc(
                color = color,
                startAngle = SPINNER_START_DEGREES,
                sweepAngle = SPINNER_SWEEP_DEGREES,
                useCenter = false,
                topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
                size = Size(ringRadius * 2, ringRadius * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
internal fun rememberAnimationsDisabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

@Preview
@Composable
private fun PerpsDepositLoadersPreview() {
    ThemedPreview(isDarkOnly = true) {
        Column {
            Row {
                for (step in 0..PERPS_STEPS_LOADER_SUCCESS) {
                    PerpsDepositStepsLoader(step = step)
                }
            }
            Row {
                PerpsDepositSpinner(size = 12.dp, radius = 5.dp)
                PerpsDepositSpinner()
                PerpsDepositSpinner(size = 28.dp, radius = 10.5.dp, strokeWidth = 3.dp)
            }
        }
    }
}
