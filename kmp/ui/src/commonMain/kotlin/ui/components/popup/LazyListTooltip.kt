package ui.components.popup

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import ui.theme.UIKit

private const val SCROLL_VIEWPORTS_PER_SECOND = 2.5f
private const val SCROLL_RAMP_SECONDS = 0.25f
private const val NANOS_PER_SECOND = 1_000_000_000f

/**
 * Where an item of a lazy list is relative to the visible part of the list, for a tooltip that
 * points at it even while the item is scrolled out of view.
 */
@Stable
class LazyListTooltipState internal constructor(internal val listState: LazyListState) {

    internal var overlay: LayoutCoordinates? = null

    internal var anchor: ItemAnchor? by mutableStateOf(null)

    internal var bubbleHeight by mutableIntStateOf(0)

    internal var lastSide = Side.Below
}

internal enum class Side { Above, Below }

/** Relative to the top of the item, so they hold while the list scrolls. */
internal data class ItemAnchor(val key: Any, val left: Int, val top: Int, val bottom: Int)

@Composable
fun rememberLazyListTooltipState(listState: LazyListState): LazyListTooltipState {
    return remember(listState) { LazyListTooltipState(listState) }
}

fun Modifier.lazyListTooltipAnchor(state: LazyListTooltipState, itemKey: Any): Modifier {
    return onGloballyPositioned { coordinates ->
        val overlay = state.overlay?.takeIf { it.isAttached } ?: return@onGloballyPositioned
        val item = state.listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == itemKey }
            ?: return@onGloballyPositioned
        val bounds = overlay.localBoundingBoxOf(coordinates, clipBounds = false)
        val left = bounds.left.roundToInt()
        val top = bounds.top.roundToInt() - item.offset
        val bottom = bounds.bottom.roundToInt() - item.offset
        val current = state.anchor
        if (current?.key != itemKey || current.left != left || current.top != top || current.bottom != bottom) {
            state.anchor = ItemAnchor(itemKey, left, top, bottom)
        }
    }
}

/** Taps on the list close the tooltip and still reach the list; a scroll does not close it. */
fun Modifier.dismissTooltipOnTap(enabled: Boolean, onDismiss: () -> Unit): Modifier {
    if (!enabled) {
        return this
    }
    return pointerInput(onDismiss) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            while (true) {
                val changes = awaitPointerEvent(PointerEventPass.Initial).changes
                val change = changes.firstOrNull { it.id == down.id } ?: break
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                    break
                }
                if (change.changedToUpIgnoreConsumed()) {
                    onDismiss()
                    break
                }
            }
        }
    }
}

/**
 * A tooltip over a lazy list that points at the item [itemKey]: above or below it while it is in
 * view, at the edge of the visible part of the list it went out through while it is not. Placed
 * in the [BoxScope] that holds the list, over the list.
 */
@Composable
fun BoxScope.LazyListTooltip(
    state: LazyListTooltipState,
    itemKey: Any,
    text: String,
    modifier: Modifier = Modifier,
    badge: String? = null,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color = UIKit.colorScheme.background.contentAttention,
    textColor: Color = UIKit.colorScheme.text.primary,
    maxWidth: Dp = 280.dp,
    gap: Dp = 4.dp,
    startPadding: Dp = 16.dp,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { gap.roundToPx() }
    val startPaddingPx = with(density) { startPadding.roundToPx() }
    val tailOnTop by remember(state, itemKey, gapPx) {
        derivedStateOf {
            state.place(itemKey, gapPx).tailOnTop
        }
    }
    Layout(
        content = {
            MoonTooltipContent(
                text = text,
                modifier = modifier.widthIn(max = maxWidth),
                tailOnTop = tailOnTop,
                badge = badge,
                onClick = onClick,
                backgroundColor = backgroundColor,
                textColor = textColor,
            )
        },
        modifier = Modifier
            .matchParentSize()
            .onPlaced { state.overlay = it },
    ) { measurables, constraints ->
        val bubble = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        state.bubbleHeight = bubble.height
        layout(constraints.maxWidth, constraints.maxHeight) {
            val placement = state.place(itemKey, gapPx)
            state.lastSide = placement.side
            val anchorX = state.anchor?.takeIf { it.key == itemKey }?.left
            val maxX = (constraints.maxWidth - bubble.width).coerceAtLeast(0)
            val x = (anchorX ?: startPaddingPx).coerceIn(0, maxX)
            bubble.place(x, placement.y)
        }
    }
}

internal class Placement(val y: Int, val tailOnTop: Boolean, val side: Side)

internal fun LazyListTooltipState.place(itemKey: Any, gapPx: Int): Placement {
    val info = listState.layoutInfo
    val top = info.viewportStartOffset + info.beforeContentPadding
    val bottom = info.viewportEndOffset - info.afterContentPadding
    val height = bubbleHeight
    val item = info.visibleItemsInfo.firstOrNull { it.key == itemKey }
    val bounds = anchor?.takeIf { it.key == itemKey }
    val anchorTop: Int
    val anchorBottom: Int
    val side: Side
    if (item == null) {
        side = lastSide
        anchorTop = if (side == Side.Below) {
            bottom
        } else {
            top
        }
        anchorBottom = anchorTop
    } else {
        anchorTop = item.offset + (bounds?.top ?: 0)
        anchorBottom = item.offset + (bounds?.bottom ?: item.size)
        side = if (anchorTop < top) {
            Side.Above
        } else {
            Side.Below
        }
    }
    val roomAbove = anchorTop.coerceAtMost(bottom) - gapPx - height >= top
    return if (roomAbove) {
        Placement(y = anchorTop.coerceAtMost(bottom) - gapPx - height, tailOnTop = false, side = side)
    } else {
        Placement(
            y = (anchorBottom.coerceAtLeast(top) + gapPx).coerceAtMost(bottom - height),
            tailOnTop = true,
            side = side,
        )
    }
}

/**
 * Scrolls the item [itemKey] to the top of the list in one motion: towards it while it is not laid
 * out yet, then easing into place once its offset is known.
 */
suspend fun LazyListTooltipState.scrollToAnchor(itemKey: Any) {
    listState.scroll {
        val direction = if (lastSide == Side.Below) {
            1f
        } else {
            -1f
        }
        var velocity = 0f
        var frame = withFrameNanos { it }
        while (listState.layoutInfo.visibleItemsInfo.none { it.key == itemKey }) {
            val info = listState.layoutInfo
            val maxVelocity = (info.viewportEndOffset - info.viewportStartOffset) * SCROLL_VIEWPORTS_PER_SECOND
            val now = withFrameNanos { it }
            val seconds = (now - frame) / NANOS_PER_SECOND
            frame = now
            velocity = (velocity + maxVelocity * seconds / SCROLL_RAMP_SECONDS).coerceAtMost(maxVelocity)
            val delta = velocity * seconds * direction
            if (delta != 0f && abs(scrollBy(delta)) < abs(delta)) {
                return@scroll
            }
        }
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.first { it.key == itemKey }
        val distance = (item.offset - info.viewportStartOffset - info.beforeContentPadding).toFloat()
        var scrolled = 0f
        animate(
            initialValue = 0f,
            targetValue = distance,
            initialVelocity = velocity * direction,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
        ) { value, _ ->
            scrolled += scrollBy(value - scrolled)
        }
    }
}
