package ui.components.moon.cell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.theme.UIKit

@Composable
fun TextCell(
    modifier: Modifier = Modifier,
    title: CharSequence,
    subtitle: CharSequence? = null,
    titleColor: Color = UIKit.colorScheme.text.primary,
    subtitleColor: Color = UIKit.colorScheme.text.secondary,
    tags: (@Composable RowScope.() -> Unit)? = null,
    description: (@Composable () -> Unit)? = null,
    image: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
    isContentHasPriority: Boolean = true,
    paddingBetween: Boolean = true,
    maxLinesTitle: Int = 1,
    maxLinesSubtitle: Int = 1,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    minHeight: Dp = DefaultItemHeight,
) {
    BaseTextCell(
        modifier = modifier,
        isContentHasPriority = isContentHasPriority,
        paddingBetween = paddingBetween,
        description = description,
        icon = image?.let {
            { image() }
        },
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MoonItemTitle(
                    text = title,
                    color = titleColor,
                    maxLines = maxLinesTitle,
                )

                tags?.invoke(this)
            }
        },
        subtitle = if (subtitle.isNullOrBlank()) {
            null
        } else {
            {
                MoonItemSubtitle(
                    text = subtitle,
                    color = subtitleColor,
                    maxLines = maxLinesSubtitle,
                )
            }
        },
        onLongClick = onLongClick,
        content = content,
        onClick = onClick,
        minHeight = minHeight,
    )
}

@Composable
fun TextCell(
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit),
    subtitle: (@Composable () -> Unit)? = null,
    description: (@Composable () -> Unit)? = null,
    image: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    minHeight: Dp = DefaultItemHeight,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
) {
    BaseTextCell(
        modifier = modifier,
        icon = image,
        title = title,
        subtitle = subtitle,
        description = description,
        content = content,
        onClick = onClick,
        minHeight = minHeight,
        verticalAlignment = verticalAlignment,
    )
}
