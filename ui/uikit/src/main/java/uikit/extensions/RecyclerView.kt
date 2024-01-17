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

            var verticalOffset = 0

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                verticalOffset += dy
                trySend(verticalOffset)
            }
        }
        addOnScrollListener(listener)
        awaitClose { removeOnScrollListener(listener) }
    }

val RecyclerView.verticalScrolled: Flow<Boolean>
    get() = verticalOffset.map {
        it > 0
    }.distinctUntilChanged()
