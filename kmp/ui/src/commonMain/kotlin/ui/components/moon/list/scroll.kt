package ui.components.moon.list

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection

@Composable
fun rememberScrollIntoViewIfNeeded(
    state: LazyListState,
): suspend (index: Int, contentPadding: PaddingValues) -> Unit {
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current

    return remember(state, layoutDirection, density) {
        { index: Int, contentPadding: PaddingValues ->
            var item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            if (item == null) {
                state.animateScrollToItem(index)
                item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            }
            item ?: return@remember

            val info = state.layoutInfo

            val startPadding = with(density) {
                contentPadding.calculateLeftPadding(layoutDirection).roundToPx()
            }
            val endPadding = with(density) {
                contentPadding.calculateRightPadding(layoutDirection).roundToPx()
            }

            val innerStart = info.viewportStartOffset + startPadding
            val innerEnd = info.viewportEndOffset - endPadding

            val itemStart = item.offset
            val itemEnd = item.offset + item.size

            val delta = when {
                itemStart < innerStart -> itemStart - innerStart
                itemEnd > innerEnd -> itemEnd - innerEnd
                else -> 0
            }

            if (delta != 0) {
                state.animateScrollBy(delta.toFloat())
            }
        }
    }
}
