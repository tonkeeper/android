package ui.components.base

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import ui.theme.UIKit

private data class ProgressIndicatorConfig(
    val box: Float,
    val stroke: Float,
    val borderColor: Color,
    val backgroundColor: Color,
    val trackColor: Color,
) {

    val trackBackgroundColor = trackColor.copy(alpha = 0.32f)
    val corner = box / 2f
    val innerK = 12f / 22f
    val innerW = box * innerK
    val innerH = box * innerK
    val innerLeft = (box - innerW) / 2f
    val innerTop = (box - innerH) / 2f

    val inset = stroke / 2f
    val arcLeft = innerLeft + inset
    val arcTop  = innerTop + inset
    val arcW = innerW - stroke
    val arcH = innerH - stroke

    val cornerRadius = CornerRadius(corner, corner)
}

@Composable
fun UIKitProgressIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    borderColor: Color = UIKit.colorScheme.background.content,
    backgroundColor: Color = UIKit.colorScheme.icon.tertiary,
    trackSize: Dp = 2.dp,
    trackColor: Color = UIKit.colorScheme.icon.primary,
) {
    val angle = produceState(0f) {
        val durationMillis = 900
        while (isActive) {
            withInfiniteAnimationFrameMillis { frameTimeMillis ->
                value = (frameTimeMillis % durationMillis) / durationMillis.toFloat() * 360f
            }
        }
    }

    val density = LocalDensity.current
    val box = with(density) { size.toPx() }
    val stroke = with(density) { trackSize.toPx() }

    val config = remember(box, stroke, borderColor, backgroundColor, trackColor) {
        ProgressIndicatorConfig(
            box = box,
            stroke = stroke,
            borderColor = borderColor,
            backgroundColor = backgroundColor,
            trackColor = trackColor,
        )
    }

    Spacer(
        modifier = modifier
            .progressSemantics()
            .size(size)
            .graphicsLayer {
                rotationZ = angle.value
            }
            .drawBehind {
                drawBg(config)
                drawTrack(config)
            }
    )
}

private fun DrawScope.drawBg(config: ProgressIndicatorConfig) {
    drawRoundRect(
        color = config.backgroundColor,
        cornerRadius = config.cornerRadius
    )

    drawRoundRect(
        color = config.borderColor,
        cornerRadius = config.cornerRadius,
        style = Stroke(width = config.stroke)
    )

    drawArc(
        color = config.trackBackgroundColor,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(config.arcLeft, config.arcTop),
        size = Size(config.arcW, config.arcH),
        style = Stroke(width = config.stroke, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawTrack(config: ProgressIndicatorConfig) {
    drawArc(
        color = config.trackColor,
        startAngle = 0f,
        sweepAngle = 80f,
        useCenter = false,
        topLeft = Offset(config.arcLeft, config.arcTop),
        size = Size(config.arcW, config.arcH),
        style = Stroke(width = config.stroke, cap = StrokeCap.Round)
    )
}