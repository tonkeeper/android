package ui.components.moon.cell

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val DefaultItemHeight = 56.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BaseTextCell(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    isContentHasPriority: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
    content: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    paddingBetween: Boolean = false,
    clip: Shape = RectangleShape,
    minHeight: Dp = DefaultItemHeight,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minHeight)
            .clip(clip)
            .run {
                if (onClick == null) {
                    this
                } else {
                    combinedClickable(onClick = onClick, onLongClick = onLongClick)
                }
            }
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(
            modifier = Modifier
                .run {
                    if (isContentHasPriority) {
                        weight(1f)
                    } else {
                        this
                    }
                },
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            if (header != null) {
                header()
                Spacer(modifier = Modifier.height(2.dp))
            }

            title()

            if (subtitle != null) {
                if (paddingBetween) {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                subtitle()
            }
        }

        if (!isContentHasPriority) {
            Spacer(modifier = Modifier.weight(1f))
        }

        if (content != null) {
            Spacer(modifier = Modifier.width(8.dp))
            content()
        }
    }
}
