package com.tonapps.portfolio.screens.list

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal const val WALLET_ROW_CONTENT_TYPE = "wallet"

private const val AUTO_SCROLL_FRAME_MS = 16L
private const val MAX_AUTO_SCROLL_STEP_PX = 40f

@Composable
fun rememberWalletsDragDropState(
    listState: LazyListState,
    onMove: (fromWalletId: String, toWalletId: String) -> Unit,
    onDragEnd: () -> Unit,
): WalletsDragDropState {
    val scope = rememberCoroutineScope()
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    return remember(listState, scope) {
        WalletsDragDropState(
            listState = listState,
            scope = scope,
            onMove = { fromWalletId, toWalletId -> currentOnMove(fromWalletId, toWalletId) },
            onDragEnd = { currentOnDragEnd() },
        )
    }
}

class WalletsDragDropState internal constructor(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (fromWalletId: String, toWalletId: String) -> Unit,
    private val onDragEnd: () -> Unit,
) {

    var draggingWalletId: String? by mutableStateOf(null)
        private set

    private var draggedDistance by mutableFloatStateOf(0f)
    private var draggingItemInitialOffset = 0
    private var lastRequestedMove: Pair<Int, String>? = null
    private var autoScrollJob: Job? = null

    private val handleBoundsByWalletId = mutableMapOf<String, Rect>()
    private var containerCoordinates: LayoutCoordinates? = null

    private val draggingItemLayoutInfo: LazyListItemInfo?
        get() = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingWalletId }

    val draggingItemOffset: Float
        get() = draggingItemLayoutInfo?.let { item ->
            draggingItemInitialOffset + draggedDistance - item.offset
        } ?: 0f

    fun registerHandle(walletId: String, boundsInRoot: Rect) {
        handleBoundsByWalletId[walletId] = boundsInRoot
    }

    fun unregisterHandle(walletId: String) {
        handleBoundsByWalletId.remove(walletId)
    }

    fun registerContainer(coordinates: LayoutCoordinates) {
        containerCoordinates = coordinates
    }

    fun findWalletIdByHandleAt(containerPosition: Offset): String? {
        val coordinates = containerCoordinates?.takeIf { it.isAttached } ?: return null
        val rootPosition = coordinates.localToRoot(containerPosition)
        return handleBoundsByWalletId.entries.firstOrNull { it.value.contains(rootPosition) }?.key
    }

    fun onDragStart(walletId: String) {
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == walletId }
            ?: return
        draggingWalletId = walletId
        draggingItemInitialOffset = item.offset
        draggedDistance = 0f
        lastRequestedMove = null
    }

    fun onDrag(deltaY: Float) {
        if (draggingWalletId == null) {
            return
        }
        draggedDistance += deltaY
        moveIfNeeded()
        autoScrollIfNeeded()
    }

    fun onDragInterrupted() {
        val hasMoved = draggingWalletId != null && lastRequestedMove != null
        draggingWalletId = null
        draggedDistance = 0f
        draggingItemInitialOffset = 0
        lastRequestedMove = null
        autoScrollJob?.cancel()
        autoScrollJob = null
        if (hasMoved) {
            onDragEnd()
        }
    }

    private fun moveIfNeeded() {
        val draggingId = draggingWalletId ?: return
        val draggingItem = draggingItemLayoutInfo ?: return
        val startOffset = draggingItem.offset + draggingItemOffset
        val middleOffset = startOffset + draggingItem.size / 2f
        val targetItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.key != draggingId &&
                item.contentType == WALLET_ROW_CONTENT_TYPE &&
                middleOffset.toInt() in item.offset until (item.offset + item.size)
        } ?: return
        val targetWalletId = targetItem.key as? String ?: return

        val move = draggingItem.index to targetWalletId
        if (move == lastRequestedMove) {
            return
        }
        lastRequestedMove = move
        onMove(draggingId, targetWalletId)
    }

    private fun autoScrollIfNeeded() {
        if (computeOverscroll() == 0f) {
            autoScrollJob?.cancel()
            autoScrollJob = null
            return
        }
        if (autoScrollJob?.isActive == true) {
            return
        }
        autoScrollJob = scope.launch {
            while (isActive && draggingWalletId != null) {
                val overscroll = computeOverscroll()
                if (overscroll == 0f) {
                    break
                }
                val step = overscroll.coerceIn(-MAX_AUTO_SCROLL_STEP_PX, MAX_AUTO_SCROLL_STEP_PX)
                if (listState.scrollBy(step) == 0f) {
                    break
                }
                moveIfNeeded()
                delay(AUTO_SCROLL_FRAME_MS)
            }
        }
    }

    private fun computeOverscroll(): Float {
        val item = draggingItemLayoutInfo ?: return 0f
        val startOffset = item.offset + draggingItemOffset
        val endOffset = startOffset + item.size
        val viewportStart = listState.layoutInfo.viewportStartOffset
        val viewportEnd = listState.layoutInfo.viewportEndOffset
        return when {
            draggedDistance > 0f -> (endOffset - viewportEnd).coerceAtLeast(0f)
            draggedDistance < 0f -> (startOffset - viewportStart).coerceAtMost(0f)
            else -> 0f
        }
    }
}
