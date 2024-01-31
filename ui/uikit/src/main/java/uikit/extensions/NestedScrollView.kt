package uikit.extensions

import android.view.View
import androidx.core.widget.NestedScrollView

fun NestedScrollView.scrollDown(smooth: Boolean = false) {
    if (childCount > 0) {
        val child = getChildAt(childCount - 1)
        scroll(child.left, child.bottom, smooth)
    }
}

fun NestedScrollView.scrollView(view: View, smooth: Boolean = false) {
    scroll(view.left, view.top, smooth)
}

fun NestedScrollView.scroll(x: Int, y: Int, smooth: Boolean = false) {
    if (smooth) {
        smoothScrollTo(x, y)
    } else {
        scrollTo(x, y)
    }
}