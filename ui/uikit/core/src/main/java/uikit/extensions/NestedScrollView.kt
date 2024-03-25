package uikit.extensions

import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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

val NestedScrollView.verticalOffset: Flow<Int>
    get() = callbackFlow {
        val listener = object : NestedScrollView.OnScrollChangeListener {

            var verticalOffset = 0

            override fun onScrollChange(
                v: NestedScrollView,
                scrollX: Int,
                scrollY: Int,
                oldScrollX: Int,
                oldScrollY: Int
            ) {
                verticalOffset = scrollY
                trySend(verticalOffset)
            }
        }
        setOnScrollChangeListener(listener)
        awaitClose()
    }

val NestedScrollView.topScrolled: Flow<Boolean>
    get() = verticalOffset.map {
        it > 0
    }.distinctUntilChanged()