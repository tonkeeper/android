package ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ui.tick

/**
 * Ripple-free press feedback: scales content down while pressed and plays the standard tick
 * haptic when the press is released as a click. Wired app-wide as the default [LocalIndication]
 * in [MoonTheme], so every `clickable`/`combinedClickable` picks it up.
 */
object ScaleIndication : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        ScaleIndicationNode(interactionSource)

    override fun equals(other: Any?): Boolean = other === this

    override fun hashCode(): Int = -1
}

/**
 * [ScaleIndication] as a plain [Modifier] for components that ignore [LocalIndication] — notably
 * Material3 `Button`. Give the component its own [interactionSource] and pass the same one here.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    hapticOnClick: Boolean = true,
): Modifier {
    val scale = remember { Animatable(1f) }
    var pressPivot by remember { mutableStateOf(Offset.Unspecified) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(interactionSource) {
        collectPressScale(
            interactionSource = interactionSource,
            scale = scale,
            onPress = { position -> pressPivot = position },
            onClickHaptic = {
                // Opt out for elements that play their own semantic haptic in onClick.
                if (hapticOnClick) haptic.tick()
            },
        )
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        transformOrigin = if (pressPivot.isSpecified && size.width > 0f && size.height > 0f) {
            TransformOrigin(
                (pressPivot.x / size.width).coerceIn(0f, 1f),
                (pressPivot.y / size.height).coerceIn(0f, 1f),
            )
        } else {
            TransformOrigin.Center
        }
    }
}

private const val PressedScale = 0.985f

// Press/release curves from design: 120ms squeeze, 420ms springy return with a slight overshoot.
private val PressDownSpec = tween<Float>(durationMillis = 120, easing = CubicBezierEasing(0.3f, 0.75f, 0.45f, 1f))
private val ReleaseSpec = tween<Float>(durationMillis = 420, easing = CubicBezierEasing(0.34f, 1.7f, 0.45f, 1f))

/**
 * Drives [scale] from [interactionSource]: shrink on press, return on release/cancel. The press
 * position is reported through [onPress] so the caller can scale around the touch point. A release
 * (a click, as opposed to a cancelled press) also plays [onClickHaptic], so every scaled element
 * gives the same tick feedback on tap.
 */
private suspend fun CoroutineScope.collectPressScale(
    interactionSource: InteractionSource,
    scale: Animatable<Float, *>,
    onPress: (Offset) -> Unit,
    onClickHaptic: () -> Unit,
) {
    var pressJob: Job? = null
    interactionSource.interactions.collect { interaction ->
        when (interaction) {
            is PressInteraction.Press -> {
                onPress(interaction.pressPosition)
                pressJob = launch { scale.animateTo(PressedScale, PressDownSpec) }
            }
            is PressInteraction.Release -> {
                onClickHaptic()
                launch {
                    // Let the shrink finish before returning, so a fast tap still shows it.
                    pressJob?.join()
                    scale.animateTo(1f, ReleaseSpec)
                }
            }
            is PressInteraction.Cancel ->
                launch {
                    pressJob?.join()
                    scale.animateTo(1f, ReleaseSpec)
                }
        }
    }
}

private class ScaleIndicationNode(
    private val interactionSource: InteractionSource
) : DrawModifierNode, CompositionLocalConsumerModifierNode, Modifier.Node() {

    private val scale = Animatable(1f)
    private var pressPivot = Offset.Unspecified

    override fun onAttach() {
        coroutineScope.launch {
            collectPressScale(
                interactionSource = interactionSource,
                scale = scale,
                onPress = { position -> pressPivot = position },
                onClickHaptic = {
                    if (isAttached) {
                        currentValueOf(LocalHapticFeedback).tick()
                    }
                },
            )
        }
    }

    override fun ContentDrawScope.draw() {
        val current = scale.value
        val pivot = if (pressPivot.isSpecified) pressPivot else center
        scale(current, current, pivot) {
            this@draw.drawContent()
        }
    }
}
