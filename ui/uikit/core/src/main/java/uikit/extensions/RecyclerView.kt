package uikit.extensions

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

