package ui.components.moon.container

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import ui.components.moon.MoonBadge
import kotlin.math.PI
import kotlin.math.sin

enum class BadgeDirection {
    EndTop,
    StartTop,
    EndBottom,
    StartBottom,
}

fun interface BadgeStrategy {
    fun calculateOffset(parentSize: IntSize, badgeSize: IntSize): IntOffset
}

@Immutable
class CircleBadgeStrategy(
    private val direction: BadgeDirection = BadgeDirection.EndTop
) : BadgeStrategy {

    override fun calculateOffset(parentSize: IntSize, badgeSize: IntSize): IntOffset {
        val cx = parentSize.width / 2f
        val cy = parentSize.height / 2f
        val radius = minOf(cx, cy)
        val d = radius * DIAGONAL

        val x = when (direction) {
            BadgeDirection.EndTop, BadgeDirection.EndBottom -> cx + d
            BadgeDirection.StartTop, BadgeDirection.StartBottom -> cx - d
        }
        val y = when (direction) {
            BadgeDirection.StartTop, BadgeDirection.EndTop -> cy - d
            BadgeDirection.EndBottom, BadgeDirection.StartBottom -> cy + d
        }

        return IntOffset(
            x = (x - badgeSize.width / 2f).toInt(),
            y = (y - badgeSize.height / 2f).toInt()
        )
    }

    private companion object {
        val DIAGONAL = sin(PI / 4).toFloat()
    }
}

@Composable
fun MoonBadgedBox(
    modifier: Modifier = Modifier,
    strategy: BadgeStrategy = CircleBadgeStrategy(),
    badge: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = {
            Box { content() }
            Box { badge?.invoke() }
        }
    ) { measurables, constraints ->
        val contentPlaceable = measurables[0].measure(constraints)
        val badgePlaceable = measurables[1].measure(constraints)

        layout(contentPlaceable.width, contentPlaceable.height) {
            contentPlaceable.place(0, 0)
            if (badgePlaceable.width > 0 && badgePlaceable.height > 0) {
                val offset = strategy.calculateOffset(
                    parentSize = IntSize(contentPlaceable.width, contentPlaceable.height),
                    badgeSize = IntSize(badgePlaceable.width, badgePlaceable.height)
                )
                badgePlaceable.place(offset.x, offset.y)
            }
        }
    }
}

@Composable
fun MoonBadgedBox(
    badge: String?,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White,
    backgroundColor: Color = Color.Red,
    direction: BadgeDirection = BadgeDirection.EndTop,
    image: @Composable () -> Unit,
) {
    MoonBadgedBox(
        modifier = modifier,
        strategy = CircleBadgeStrategy(direction),
        badge = if (!badge.isNullOrBlank()) {
            {
                MoonBadge(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    textColor = contentColor,
                    bgColor = backgroundColor
                )
            }
        } else {
            null
        },
        content = image,
    )
}

private class CutoutInfo {
    var center: Offset = Offset.Zero
    var radius: Float = 0f
}

@Composable
fun MoonCutBadgedBox(
    modifier: Modifier = Modifier,
    direction: BadgeDirection = BadgeDirection.EndBottom,
    cutPadding: Dp = 2.dp,
    badge: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    MoonCutBadgedBox(
        modifier = modifier,
        strategy = CircleBadgeStrategy(direction),
        cutPadding = cutPadding,
        badge = badge,
        content = content,
    )
}

@Composable
fun MoonCutBadgedBox(
    modifier: Modifier = Modifier,
    strategy: BadgeStrategy = CircleBadgeStrategy(),
    cutPadding: Dp = 2.dp,
    badge: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val cutout = remember { CutoutInfo() }
    val cutPaddingPx = with(LocalDensity.current) { cutPadding.toPx() }

    Layout(
        modifier = modifier,
        content = {
            Box(
                Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        if (cutout.radius > 0f) {
                            drawCircle(
                                color = Color.Black,
                                radius = cutout.radius,
                                center = cutout.center,
                                blendMode = BlendMode.Clear
                            )
                        }
                    }
            ) { content() }
            Box { badge?.invoke() }
        }
    ) { measurables, constraints ->
        val contentPlaceable = measurables[0].measure(constraints)
        val badgePlaceable = measurables[1].measure(constraints)

        val hasBadge = badgePlaceable.width > 0 && badgePlaceable.height > 0
        val badgeOffset = if (hasBadge) {
            strategy.calculateOffset(
                parentSize = IntSize(contentPlaceable.width, contentPlaceable.height),
                badgeSize = IntSize(badgePlaceable.width, badgePlaceable.height)
            )
        } else {
            IntOffset.Zero
        }

        if (hasBadge) {
            cutout.center = Offset(
                badgeOffset.x + badgePlaceable.width / 2f,
                badgeOffset.y + badgePlaceable.height / 2f
            )
            cutout.radius = maxOf(badgePlaceable.width, badgePlaceable.height) / 2f + cutPaddingPx
        } else {
            cutout.radius = 0f
        }

        layout(contentPlaceable.width, contentPlaceable.height) {
            contentPlaceable.place(0, 0)
            if (hasBadge) {
                badgePlaceable.place(badgeOffset.x, badgeOffset.y)
            }
        }
    }
}

enum class OverlapDirection {

    /** Later items are drawn on top of earlier items */
    EndOnTop,

    /** Earlier items are drawn on top of later items */
    StartOnTop,
}

@Composable
fun MoonCutRow(
    modifier: Modifier = Modifier,
    overlap: Dp = 4.dp,
    cutPadding: Dp = 2.dp,
    direction: OverlapDirection = OverlapDirection.EndOnTop,
    content: @Composable () -> Unit,
) {
    val cutPaddingPx = with(LocalDensity.current) { cutPadding.toPx() }
    val overlapPx = with(LocalDensity.current) { overlap.roundToPx() }

    Layout(
        modifier = modifier,
        content = content,
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        if (placeables.isEmpty()) {
            return@Layout layout(0, 0) {}
        }

        val xPositions = IntArray(placeables.size)
        var currentX = 0
        placeables.forEachIndexed { index, placeable ->
            xPositions[index] = currentX
            currentX += placeable.width - overlapPx
        }

        val totalWidth = xPositions.last() + placeables.last().width
        val totalHeight = placeables.maxOf { it.height }

        layout(totalWidth, totalHeight) {
            placeables.forEachIndexed { index, placeable ->
                val y = (totalHeight - placeable.height) / 2
                val zIndex = when (direction) {
                    OverlapDirection.EndOnTop -> index.toFloat()
                    OverlapDirection.StartOnTop -> (placeables.size - index).toFloat()
                }

                val overlapperIndex = when (direction) {
                    OverlapDirection.EndOnTop -> (index + 1).takeIf { it <= placeables.lastIndex }
                    OverlapDirection.StartOnTop -> (index - 1).takeIf { it >= 0 }
                }

                if (overlapperIndex != null) {
                    val overlapper = placeables[overlapperIndex]
                    val cutCenter = Offset(
                        (xPositions[overlapperIndex] - xPositions[index]) + overlapper.width / 2f,
                        placeable.height / 2f
                    )
                    val cutRadius = maxOf(overlapper.width, overlapper.height) / 2f + cutPaddingPx
                    placeable.placeWithLayer(xPositions[index], y, zIndex = zIndex) {
                        clip = true
                        shape = CircleCutoutShape(cutCenter, cutRadius)
                    }
                } else {
                    placeable.place(xPositions[index], y, zIndex = zIndex)
                }
            }
        }
    }
}

private class CircleCutoutShape(
    private val center: Offset,
    private val radius: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, size.width, size.height))
            addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
        }
        return Outline.Generic(path)
    }
}
