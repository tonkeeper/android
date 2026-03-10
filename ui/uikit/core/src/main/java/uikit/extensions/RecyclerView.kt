package uikit.extensions

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

val RecyclerView.verticalOffset: Flow<Int>
    get() = callbackFlow {
        val listener = object : RecyclerView.OnScrollListener() {

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                trySend(computeVerticalScrollOffset())
            }
        }
        addOnScrollListener(listener)
        trySend(computeVerticalScrollOffset())
        awaitClose { removeOnScrollListener(listener) }
    }

val RecyclerView.isMaxScrollReached: Boolean
    get() {
        val maxScroll = computeVerticalScrollRange()
        val currentScroll = computeVerticalScrollOffset() + computeVerticalScrollExtent()
        return currentScroll >= maxScroll
    }

val RecyclerView.topScrolled: Flow<Boolean>
    get() = verticalOffset.map {
        it > 0
    }.distinctUntilChanged()

val RecyclerView.bottomScrolled: Flow<Boolean>
    get() = verticalOffset.map {
        !isMaxScrollReached
    }.distinctUntilChanged()

fun RecyclerView.hideKeyboardWhenScroll() {
    addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dy > 0) {
                hideKeyboard()
            }
        }
    })
}

fun RecyclerView.smartScrollTo(position: Int) {
    val layoutManager = layoutManager as? LinearLayoutManager ?: return
    val firstVisible = layoutManager.findFirstVisibleItemPosition()
    val lastVisible = layoutManager.findLastVisibleItemPosition()

    if (position in firstVisible..lastVisible) {
        return
    }
    if (position < firstVisible) {
        val scrollTo = maxOf(0, position - 3)
        layoutManager.scrollToPositionWithOffset(scrollTo, 0)
    } else {
        val scrollTo = maxOf(0, position - 3)
        layoutManager.scrollToPositionWithOffset(scrollTo, 0)
    }
}

