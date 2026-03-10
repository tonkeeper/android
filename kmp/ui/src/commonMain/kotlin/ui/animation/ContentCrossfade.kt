package ui.animation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun <T> ContentCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    label: String = "AnimatedContent",
    duration: Int = 240,
    content: @Composable() AnimatedContentScope.(targetState: T) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            fadeIn(animationSpec = tween(
                durationMillis = duration,
                easing = FastOutSlowInEasing
            )) togetherWith fadeOut(animationSpec = tween(
                durationMillis = duration,
                easing = FastOutSlowInEasing
            )) using SizeTransform(clip = false)
        },
        contentAlignment = contentAlignment,
        label = label,
        content = content
    )
}