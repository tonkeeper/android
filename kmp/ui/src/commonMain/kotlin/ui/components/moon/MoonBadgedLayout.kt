package ui.components.moon

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
        } else null,
        content = image,
    )
}
