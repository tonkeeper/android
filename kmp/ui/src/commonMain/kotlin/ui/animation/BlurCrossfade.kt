package ui.animation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp

@Composable
fun <T> BlurCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable (targetState: T) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            fadeIn(animationSpec = tween(BLUR_CROSSFADE_MS, easing = EaseInOut)) togetherWith
                fadeOut(animationSpec = tween(BLUR_CROSSFADE_MS, easing = EaseInOut)) using
                SizeTransform(clip = false)
        },
        contentAlignment = contentAlignment,
        label = "BlurCrossfade",
    ) { state ->
        val blur by transition.animateDp(
            transitionSpec = { tween(BLUR_CROSSFADE_MS, easing = EaseInOut) },
            label = "BlurCrossfadeRadius",
        ) { enterExit ->
            if (enterExit == EnterExitState.Visible) {
                0.dp
            } else {
                BlurCrossfadeRadius
            }
        }
        Box(modifier = Modifier.blur(blur, BlurredEdgeTreatment.Unbounded)) {
            content(state)
        }
    }
}

private const val BLUR_CROSSFADE_MS = 350
private val BlurCrossfadeRadius = 8.dp
