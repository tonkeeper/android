package ui.animation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private const val SHAKE_STEP_MS = 80
private const val SHAKE_DURATION_MS = SHAKE_STEP_MS * 5
private val SHAKE_WIDE_OFFSET = 8.dp
private val SHAKE_NARROW_OFFSET = 6.dp

// Returns the Animatable rather than its value so callers read it inside a draw lambda
// (graphicsLayer) and the animation doesn't recompose the caller every frame.
@Composable
fun rememberShakeOffset(active: Boolean): Animatable<Float, AnimationVector1D> {
    val shake = remember { Animatable(0f) }
    val density = LocalDensity.current

    LaunchedEffect(active) {
        if (!active) {
            // Drops the residual offset when the trigger resets mid-shake.
            shake.snapTo(0f)
            return@LaunchedEffect
        }
        val wide = with(density) { SHAKE_WIDE_OFFSET.toPx() }
        val narrow = with(density) { SHAKE_NARROW_OFFSET.toPx() }
        shake.snapTo(0f)
        shake.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = SHAKE_DURATION_MS
                -wide at SHAKE_STEP_MS
                wide at SHAKE_STEP_MS * 2
                -narrow at SHAKE_STEP_MS * 3
                narrow at SHAKE_STEP_MS * 4
            },
        )
    }

    return shake
}
