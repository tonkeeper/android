package com.tonapps.perps.screens.depositstatus

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val perpsDepositFadeEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
internal val perpsDepositSlideEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

internal const val PERPS_DEPOSIT_REDUCED_FADE_MS = 200

private const val CSS_BLUR_TO_RADIUS = 1.732f
private const val MIN_VISIBLE_BLUR = 0.1f
private const val CONTENT_BLUR = 8f
private const val CONTENT_SHIFT = 6f
private const val CONTENT_FADE_MS = 300
private const val CONTENT_BLUR_MS = 400
private const val CONTENT_SHIFT_MS = 500

@Stable
internal class PerpsDepositRevealMotion(visible: Boolean) {

    val alpha = Animatable(visible.toFraction())
    val blur = Animatable(0f)
    val shift = Animatable(0f)

    suspend fun reveal(delayMs: Long) {
        alpha.snapTo(0f)
        blur.snapTo(CONTENT_BLUR)
        shift.snapTo(CONTENT_SHIFT)
        delay(delayMs)
        coroutineScope {
            launch { alpha.animateTo(1f, tween(CONTENT_FADE_MS, easing = perpsDepositFadeEasing)) }
            launch { blur.animateTo(0f, tween(CONTENT_BLUR_MS, easing = perpsDepositFadeEasing)) }
            launch { shift.animateTo(0f, tween(CONTENT_SHIFT_MS, easing = perpsDepositSlideEasing)) }
        }
    }

    suspend fun show() {
        alpha.snapTo(1f)
        blur.snapTo(0f)
        shift.snapTo(0f)
    }
}

internal fun Modifier.perpsDepositReveal(motion: PerpsDepositRevealMotion): Modifier = graphicsLayer {
    alpha = motion.alpha.value
    translationY = motion.shift.value.dp.toPx()
    renderEffect = perpsDepositBlur(motion.blur.value.dp.toPx())
}

internal fun Boolean.toFraction(): Float = if (this) {
    1f
} else {
    0f
}

internal fun perpsDepositBlur(cssBlurPx: Float): BlurEffect? {
    if (cssBlurPx < MIN_VISIBLE_BLUR) {
        return null
    }
    val radius = cssBlurPx * CSS_BLUR_TO_RADIUS
    return BlurEffect(radius, radius, TileMode.Decal)
}
