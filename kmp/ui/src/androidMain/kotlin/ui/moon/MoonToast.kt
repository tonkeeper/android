package ui.moon

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ui.theme.Dimens
import ui.theme.UIKit
import kotlin.coroutines.resume

private const val AnimationDuration = 160
private const val AutoDismissDelay = 2000L
private val ToastShape = RoundedCornerShape(24.dp)

data class MoonToastData internal constructor(
    val text: CharSequence,
    val loading: Boolean,
    val color: Color,
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
    ) {
        mutex.withLock {
            try {
                suspendCancellableCoroutine { continuation ->
                    currentData = MoonToastData(
                        text = text,
                        loading = loading,
                        color = color,
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

    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = data != null

    LaunchedEffect(data) {
        if (data != null) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            if (!data.loading) {
                delay(AutoDismissDelay)
                hostState.dismiss()
            }
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
            .padding(top = statusBarPadding + 24.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = slideInVertically(
                initialOffsetY = { -it * 2 },
                animationSpec = tween(AnimationDuration),
            ),
            exit = slideOutVertically(
                targetOffsetY = { -it * 2 },
                animationSpec = tween(AnimationDuration),
            ),
        ) {
            lastData?.let { toast(it) }
        }
    }
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

    Row(
        modifier = modifier
            .clip(ToastShape)
            .background(backgroundColor)
            .padding(horizontal = 24.dp, vertical = Dimens.offsetMedium),
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
            Spacer(modifier = Modifier.width(10.dp))
        }
        Text(
            text = data.text.toString(),
            style = UIKit.typography.label2,
            color = colors.text.primary,
        )
    }
}
