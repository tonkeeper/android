package ui

import androidx.compose.foundation.lazy.LazyListState

val LazyListState.isAtTop: Boolean
    get() = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0