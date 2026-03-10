package ui.components.popup

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import kotlin.math.max
import kotlin.math.min

@Immutable
internal data class ActionMenuPositionProvider(
    val contentOffset: DpOffset,
    val density: Density,
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

        val initialX = when (layoutDirection) {
            LayoutDirection.Ltr -> anchorBounds.left + offsetX
            LayoutDirection.Rtl -> (anchorBounds.right - popupContentSize.width - offsetX)
        }

        val initialY = anchorBounds.bottom + offsetY

        val adjustedX = when {
            initialX + popupContentSize.width > windowSize.width -> {
                val rightAligned = windowSize.width - popupContentSize.width
                max(0, rightAligned)
            }
            initialX < 0 -> 0
            else -> initialX
        }

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