package ui

import android.os.Build
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.window.DialogWindowProvider
import uikit.extensions.removeSwipeBackExclusionRect
import uikit.extensions.setSwipeBackExclusionRect

@Composable
actual fun Modifier.platformNestedScrollInterop(): Modifier = this.nestedScroll(rememberNestedScrollInteropConnection())

@Composable
actual fun DisableNavigationBarContrast() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect {
        if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }
}

@Composable
actual fun Modifier.excludeFromBackGestures(): Modifier {
    val view = LocalView.current
    val exclusionOwner = remember { Any() }
    DisposableEffect(view, exclusionOwner) {
        onDispose { view.removeSwipeBackExclusionRect(exclusionOwner) }
    }
    return this
        .systemGestureExclusion()
        .onGloballyPositioned { coordinates ->
            view.setSwipeBackExclusionRect(exclusionOwner, coordinates.boundsInRoot().toAndroidRect())
        }
        .pointerInput(view) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                view.parent?.requestDisallowInterceptTouchEvent(true)
            }
        }
}
