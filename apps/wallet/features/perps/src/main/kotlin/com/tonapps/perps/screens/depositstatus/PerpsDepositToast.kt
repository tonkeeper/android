package com.tonapps.perps.screens.depositstatus

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import ui.confirm
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonSmallItemTitle
import ui.preview.ThemedPreview
import ui.theme.UIKit

private const val TOAST_SHIFT = -56f
private const val TOAST_START_SCALE = 0.86f
private const val TOAST_BLUR = 14f
private const val TOAST_FADE_MS = 280
private const val TOAST_BLUR_MS = 420
private const val TOAST_HIDE_MS = 200
private const val TOAST_SPRING_DAMPING = 0.72f
private const val TOAST_SPRING_STIFFNESS = 237f

@Stable
private class PerpsDepositToastMotion {

    val alpha = Animatable(0f)
    val blur = Animatable(0f)
    val shift = Animatable(0f)
    val scale = Animatable(1f)

    suspend fun appear(motion: Boolean) = coroutineScope {
        if (!motion) {
            alpha.animateTo(1f, tween(PERPS_DEPOSIT_REDUCED_FADE_MS))
            return@coroutineScope
        }
        blur.snapTo(TOAST_BLUR)
        shift.snapTo(TOAST_SHIFT)
        scale.snapTo(TOAST_START_SCALE)
        val bounce = spring<Float>(dampingRatio = TOAST_SPRING_DAMPING, stiffness = TOAST_SPRING_STIFFNESS)
        launch { alpha.animateTo(1f, tween(TOAST_FADE_MS, easing = perpsDepositFadeEasing)) }
        launch { blur.animateTo(0f, tween(TOAST_BLUR_MS, easing = perpsDepositFadeEasing)) }
        launch { shift.animateTo(0f, bounce) }
        scale.animateTo(1f, bounce)
    }

    suspend fun disappear() {
        alpha.animateTo(0f, tween(TOAST_HIDE_MS, easing = perpsDepositFadeEasing))
    }
}

@Composable
internal fun PerpsDepositToastHost(
    toast: PerpsDepositToast?,
    modifier: Modifier = Modifier,
) {
    val motionEnabled = !rememberAnimationsDisabled()
    val haptic = LocalHapticFeedback.current
    val motion = remember { PerpsDepositToastMotion() }
    LaunchedEffect(toast) {
        if (toast == PerpsDepositToast.COMPLETED) {
            haptic.confirm()
        }
    }
    var shown by remember { mutableStateOf(toast) }
    if (toast != null) {
        shown = toast
    }

    LaunchedEffect(toast != null) {
        if (toast != null) {
            motion.appear(motionEnabled)
        } else {
            motion.disappear()
            shown = null
        }
    }

    val current = shown ?: return
    PerpsDepositToastPill(
        toast = current,
        modifier = modifier.graphicsLayer {
            alpha = motion.alpha.value
            translationY = motion.shift.value.dp.toPx()
            scaleX = motion.scale.value
            scaleY = motion.scale.value
            renderEffect = perpsDepositBlur(motion.blur.value.dp.toPx())
        },
    )
}

@Composable
private fun PerpsDepositToastPill(
    toast: PerpsDepositToast,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(UIKit.colorScheme.background.contentTint)
            .padding(
                start = toastStartPadding(toast),
                top = 14.dp,
                end = 24.dp,
                bottom = 14.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (toast == PerpsDepositToast.COMPLETED) {
            MoonItemIcon(
                painter = painterResource(UIKitIcon.ic_checkmark_circle_16),
                size = 16.dp,
                color = UIKit.colorScheme.accent.green,
            )
        }
        MoonSmallItemTitle(
            text = stringResource(
                when (toast) {
                    PerpsDepositToast.IN_PROGRESS -> Localization.perps_deposit_in_progress
                    PerpsDepositToast.COMPLETED -> Localization.perps_deposit_completed
                },
            ),
        )
    }
}

private fun toastStartPadding(toast: PerpsDepositToast): Dp = when (toast) {
    PerpsDepositToast.COMPLETED -> 16.dp
    PerpsDepositToast.IN_PROGRESS -> 24.dp
}

@Preview
@Composable
private fun PerpsDepositToastPreview() {
    ThemedPreview(isDarkOnly = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PerpsDepositToastPill(toast = PerpsDepositToast.IN_PROGRESS)
            PerpsDepositToastPill(toast = PerpsDepositToast.COMPLETED)
        }
    }
}
