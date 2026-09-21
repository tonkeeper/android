package ui.components.popup

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.max
import kotlin.math.min
import ui.theme.Dimens

@Immutable
internal data class ActionMenuPositionProvider(
    val contentOffset: DpOffset,
    val density: Density,
    val horizontalAlignment: ActionMenuHorizontalAlignment = ActionMenuHorizontalAlignment.Start,
    val onPositionCalculated: (anchorBounds: IntRect, menuBounds: IntRect) -> Unit = { _, _ -> }
): PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val offsetX = with(density) { contentOffset.x.roundToPx() }
        val offsetY = with(density) { contentOffset.y.roundToPx() }

        val initialX = when (horizontalAlignment) {
            ActionMenuHorizontalAlignment.Center -> {
                val anchorCenterX = anchorBounds.left + anchorBounds.width / 2
                anchorCenterX - popupContentSize.width / 2 + offsetX
            }
            ActionMenuHorizontalAlignment.Start -> when (layoutDirection) {
                LayoutDirection.Ltr -> anchorBounds.left + offsetX
                LayoutDirection.Rtl -> anchorBounds.right - popupContentSize.width - offsetX
            }
        }

        val initialY = anchorBounds.bottom + offsetY

        val endMarginPx = with(density) { Dimens.offsetMedium.roundToPx() }
        val maxLeftX = max(0, windowSize.width - popupContentSize.width - endMarginPx)
        val adjustedX = initialX.coerceIn(0, maxLeftX)

        val adjustedY = when {
            initialY + popupContentSize.height > windowSize.height -> {
                val aboveAnchor = anchorBounds.top - popupContentSize.height - offsetY
                if (aboveAnchor >= 0) {
                    aboveAnchor
                } else {
                    val spaceBelow = windowSize.height - anchorBounds.bottom
                    val spaceAbove = anchorBounds.top

                    if (spaceBelow >= spaceAbove) {
                        min(initialY, windowSize.height - popupContentSize.height)
                    } else {
                        max(0, aboveAnchor)
                    }
                }
            }
            initialY < 0 -> 0
            else -> initialY
        }

        val menuOffset = IntOffset(adjustedX, adjustedY)
        onPositionCalculated(anchorBounds, IntRect(offset = menuOffset, size = popupContentSize))
        return menuOffset
    }
}

internal fun calculateTransformOrigin(anchorBounds: IntRect, menuBounds: IntRect): TransformOrigin {
    val pivotX = when {
        menuBounds.left >= anchorBounds.right -> 0f
        menuBounds.right <= anchorBounds.left -> 1f
        menuBounds.width == 0 -> 0f
        else -> {
            val intersectionCenter = (max(anchorBounds.left, menuBounds.left) + min(anchorBounds.right, menuBounds.right)) / 2
            (intersectionCenter - menuBounds.left).toFloat() / menuBounds.width
        }
    }

    val pivotY = when {
        menuBounds.top >= anchorBounds.bottom -> 0f
        menuBounds.bottom <= anchorBounds.top -> 1f
        menuBounds.height == 0 -> 0f
        else -> {
            val intersectionCenter = (max(anchorBounds.top, menuBounds.top) + min(anchorBounds.bottom, menuBounds.bottom)) / 2
            (intersectionCenter - menuBounds.top).toFloat() / menuBounds.height
        }
    }

    return TransformOrigin(pivotX, pivotY)
}

internal object AnchorTooltipTailSpec {
    val insetFromStart: Dp = 18.dp
    val width: Dp = 14.dp
    val height: Dp = 6.dp
    val horizontalScreenMargin: Dp = 16.dp
    val minBubbleWidth: Dp = 120.dp
    val absoluteMaxBubbleWidth: Dp = 288.dp
}

internal fun computeAnchorTooltipMaxBubbleWidthPx(
    anchorBounds: IntRect,
    windowWidthPx: Int,
    density: Density,
): Int = with(density) {
    val anchorCenterX = anchorBounds.left + anchorBounds.width / 2
    val tailCenterPx = (AnchorTooltipTailSpec.insetFromStart + AnchorTooltipTailSpec.width / 2).roundToPx()
    val idealLeft = anchorCenterX - tailCenterPx
    val marginPx = AnchorTooltipTailSpec.horizontalScreenMargin.roundToPx()
    val minPx = AnchorTooltipTailSpec.minBubbleWidth.roundToPx()
    val capPx = AnchorTooltipTailSpec.absoluteMaxBubbleWidth.roundToPx()
    (windowWidthPx - marginPx - idealLeft.coerceAtLeast(0))
        .coerceIn(minPx, capPx)
}

/**
 * @param onTailOnTopOfBubble true = хвост сверху бабла (остриё вверх, подсказка под якорём);
 * false = хвост снизу (остриё вниз, подсказка над якорём).
 */
internal class AnchorTooltipPositionProvider(
    private val gapBelowAnchor: Dp,
    private val density: Density,
    private val onTailOnTopOfBubble: (Boolean) -> Unit,
    private val preferAbove: Boolean = false,
    private val centerTail: Boolean = false,
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val gapPx = with(density) { gapBelowAnchor.roundToPx() }
        val anchorCenterX = anchorBounds.left + anchorBounds.width / 2
        val tailCenterFromStartPx = with(density) {
            (AnchorTooltipTailSpec.insetFromStart + AnchorTooltipTailSpec.width / 2).roundToPx()
        }
        val maxBubblePx = computeAnchorTooltipMaxBubbleWidthPx(
            anchorBounds = anchorBounds,
            windowWidthPx = windowSize.width,
            density = density,
        )
        val x = if (centerTail) {
            val marginPx = with(density) { AnchorTooltipTailSpec.horizontalScreenMargin.roundToPx() }
            val maxLeft = max(marginPx, windowSize.width - marginPx - popupContentSize.width)
            (anchorCenterX - popupContentSize.width / 2).coerceIn(marginPx, maxLeft)
        } else {
            val idealLeft = anchorCenterX - tailCenterFromStartPx
            idealLeft.coerceIn(0, max(0, windowSize.width - maxBubblePx))
        }

        val belowY = anchorBounds.bottom + gapPx
        val aboveY = anchorBounds.top - popupContentSize.height - gapPx

        var y: Int
        var tailOnTopOfBubble: Boolean
        if (preferAbove) {
            if (aboveY >= 0) {
                y = aboveY
                tailOnTopOfBubble = false
            } else {
                y = belowY
                tailOnTopOfBubble = true
            }
        } else {
            y = belowY
            tailOnTopOfBubble = true
            if (belowY + popupContentSize.height > windowSize.height && aboveY >= 0) {
                y = aboveY
                tailOnTopOfBubble = false
            }
        }
        y = y.coerceIn(0, max(0, windowSize.height - popupContentSize.height))

        onTailOnTopOfBubble(tailOnTopOfBubble)
        return IntOffset(x, y)
    }
}