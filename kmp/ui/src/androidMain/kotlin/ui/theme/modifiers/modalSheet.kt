package ui.theme.modifiers

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.IntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

@Composable
fun Modifier.modalSheetMaxHeight(): Modifier {
    val statusBarTop = rememberWindowStatusBarTop()
    return layout { measurable, constraints ->
        val sheetConstraints = if (constraints.hasBoundedHeight) {
            val maxHeightBelowStatusBar = (constraints.maxHeight - statusBarTop.intValue)
                .coerceAtLeast(constraints.minHeight)
            constraints.copy(maxHeight = maxHeightBelowStatusBar)
        } else {
            constraints
        }
        val placeable = measurable.measure(sheetConstraints)
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }
}

@Composable
private fun rememberWindowStatusBarTop(): IntState {
    val view = LocalView.current
    val statusBarTop = remember(view) { mutableIntStateOf(view.windowStatusBarTop()) }
    DisposableEffect(view) {
        val observer = view.viewTreeObserver
        val onGlobalLayout = ViewTreeObserver.OnGlobalLayoutListener {
            statusBarTop.intValue = view.windowStatusBarTop()
        }
        observer.addOnGlobalLayoutListener(onGlobalLayout)
        onDispose {
            val liveObserver = if (observer.isAlive) {
                observer
            } else {
                view.viewTreeObserver
            }
            liveObserver.removeOnGlobalLayoutListener(onGlobalLayout)
        }
    }
    return statusBarTop
}

private fun View.windowStatusBarTop(): Int {
    return ViewCompat.getRootWindowInsets(this)
        ?.getInsets(WindowInsetsCompat.Type.statusBars())
        ?.top
        ?: 0
}
