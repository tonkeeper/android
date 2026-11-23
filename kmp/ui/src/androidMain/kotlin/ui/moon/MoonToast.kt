package ui.moon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import androidx.annotation.DrawableRes
import androidx.compose.ui.res.painterResource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ui.confirm
import ui.reject
import ui.softTap
import ui.theme.UIKit
import uikit.widget.ToastFeedback
import uikit.widget.ToastPresence
import kotlin.coroutines.resume

private const val ShowDuration = 300
private const val HideDuration = 200
private const val AutoDismissDelay = 2000L
private val ToastSlideOffset = 8.dp
private val ToastShape = RoundedCornerShape(24.dp)
private val ToastTopOffset = 8.dp
private val ToastMinHeight = 48.dp
private val ToastElevation = 4.dp
private val ToastVerticalPadding = 14.dp
private val ToastHorizontalPadding = 24.dp
private val ToastHorizontalPaddingWithIcon = 16.dp
private val ToastIconSpacing = 8.dp

val MoonToastEasing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

data class MoonToastData internal constructor(
    val text: CharSequence,
    val loading: Boolean,
    val color: Color,
    @DrawableRes val icon: Int?,
    val iconColor: Color,
    val feedback: ToastFeedback,
    private val continuation: CancellableContinuation<Unit>,
) {
    internal fun dismiss() {
        if (continuation.isActive) {
            continuation.resume(Unit)
        }
    }
}

@Stable
class MoonToastHostState {

    var currentData by mutableStateOf<MoonToastData?>(null)
        private set

    private val mutex = Mutex()

    suspend fun showToast(
        text: CharSequence,
        loading: Boolean = false,
        color: Color = Color.Unspecified,
        @DrawableRes icon: Int? = null,
        iconColor: Color = Color.Unspecified,
        feedback: ToastFeedback = ToastFeedback.Neutral,
    ) {
        mutex.withLock {
            try {
                suspendCancellableCoroutine { continuation ->
                    currentData = MoonToastData(
                        text = text,
                        loading = loading,
                        color = color,
                        icon = icon,
                        iconColor = iconColor,
                        feedback = feedback,
                        continuation = continuation,
                    )
                }
            } finally {
                currentData = null
            }
        }
    }

    fun dismiss() {
        currentData?.dismiss()
    }
}

@Composable
fun rememberMoonToastHostState(): MoonToastHostState = remember { MoonToastHostState() }

@Composable
fun MoonToastHost(
    hostState: MoonToastHostState,
    modifier: Modifier = Modifier,
    toast: @Composable (MoonToastData) -> Unit = { MoonToastContent(it) },
) {
    val data = hostState.currentData
    val haptic = LocalHapticFeedback.current
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Keep the last non-null data so the content is still rendered during exit animation
    var lastData by remember { mutableStateOf<MoonToastData?>(null) }
    if (data != null) {
        lastData = data
    }
    val shown = lastData != null
    DisposableEffect(hostState, shown) {
        ToastPresence.set(hostState, shown)
        onDispose { ToastPresence.set(hostState, false) }
    }

    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = data != null

    LaunchedEffect(data) {
        if (data != null && !data.loading) {
            when (data.feedback) {
                ToastFeedback.Neutral -> haptic.softTap()
                ToastFeedback.Success -> haptic.confirm()
                ToastFeedback.Failure -> haptic.reject()
            }
            delay(AutoDismissDelay)
            hostState.dismiss()
        }
    }

    // Wait for the exit animation to finish, then clear the retained data
    LaunchedEffect(data) {
        if (data == null && lastData != null) {
            // Wait until the transition is fully idle (exit animation done)
            snapshotFlow { visibleState.isIdle && !visibleState.currentState }
                .filter { it }
                .first()
            lastData = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = statusBarPadding + ToastTopOffset),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = moonToastEnter(),
            exit = moonToastExit(),
        ) {
            lastData?.let { toast(it) }
        }
    }
}

@Composable
fun moonToastEnter(): EnterTransition {
    val offset = with(LocalDensity.current) { ToastSlideOffset.roundToPx() }
    return fadeIn(tween(ShowDuration, easing = MoonToastEasing)) +
        slideInVertically(tween(ShowDuration, easing = MoonToastEasing)) { -offset }
}

@Composable
fun moonToastExit(): ExitTransition {
    val offset = with(LocalDensity.current) { ToastSlideOffset.roundToPx() }
    return fadeOut(tween(HideDuration, easing = EaseIn)) +
        slideOutVertically(tween(HideDuration, easing = EaseIn)) { -offset }
}

@Composable
fun MoonToastContent(
    data: MoonToastData,
    modifier: Modifier = Modifier,
) {
    val colors = UIKit.colorScheme
    val backgroundColor = if (data.color == Color.Unspecified) {
        colors.background.contentTint
    } else {
        data.color
    }

    val startPadding = if (data.loading || data.icon != null) {
        ToastHorizontalPaddingWithIcon
    } else {
        ToastHorizontalPadding
    }
    Row(
        modifier = modifier
            .shadow(ToastElevation, ToastShape)
            .clip(ToastShape)
            .background(backgroundColor)
            .heightIn(min = ToastMinHeight)
            .padding(
                start = startPadding,
                end = ToastHorizontalPadding,
                top = ToastVerticalPadding,
                bottom = ToastVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (data.loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.32f),
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(ToastIconSpacing))
        } else if (data.icon != null) {
            Icon(
                modifier = Modifier.size(16.dp),
                painter = painterResource(data.icon),
                contentDescription = null,
                tint = data.iconColor,
            )
            Spacer(modifier = Modifier.width(ToastIconSpacing))
        }
        Text(
            text = data.text.toString(),
            style = UIKit.typography.label2,
            color = colors.text.primary,
        )
    }
}
