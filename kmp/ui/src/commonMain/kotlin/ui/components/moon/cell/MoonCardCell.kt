package ui.components.moon.cell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val DefaultCardHeight = 76.dp

@Composable
fun MoonCardCell(
    modifier: Modifier = Modifier,
    title: CharSequence,
    subtitle: CharSequence,
    image: (@Composable () -> Unit)? = null,
    minHeight: Dp = DefaultCardHeight,
    position: MoonBundlePosition = MoonBundlePosition.Default,
    onClick: (() -> Unit)? = null,
    maxLinesTitle: Int = 1,
    maxLinesSubtitle: Int = 1,
) {
    MoonBundleCell(
        modifier = modifier,
        position = position,
        content = {
            TextCell(
                image = image,
                title = title,
                subtitle = subtitle,
                minHeight = minHeight,
                maxLinesTitle = maxLinesTitle,
                maxLinesSubtitle = maxLinesSubtitle,
            )
        },
        onClick = onClick,
    )
}

@Composable
fun MoonCardCell(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    image: (@Composable () -> Unit)? = null,
    minHeight: Dp = DefaultCardHeight,
    position: MoonBundlePosition = MoonBundlePosition.Default,
    onClick: (() -> Unit)? = null,
) {
    MoonBundleCell(
        modifier = modifier,
        position = position,
        content = {
            TextCell(
                image = image,
                title = title,
                subtitle = subtitle,
                minHeight = minHeight
            )
        },
        onClick = onClick,
    )
}
