package ui.components.moon

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ui.theme.UIKit

@Composable
fun MoonExpandableText(
    modifier: Modifier = Modifier,
    backgroundColor: Color = UIKit.colorScheme.background.content,
    text: String,
    style: TextStyle,
    color: Color,
    showMoreText: String,
    showMoreColor: Color = UIKit.colorScheme.text.accent,
    maxLines: Int = 3,
) {
    val effectiveMaxLines = maxLines.coerceAtLeast(1)
    val interactionSource = remember { MutableInteractionSource() }
    var isExpanded by remember { mutableStateOf(false) }
    var isOverflowing by remember(text, maxLines) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .animateContentSize()
            .clickable(
                enabled = !isExpanded && isOverflowing,
                interactionSource = interactionSource,
                indication = null,
            ) {
                isExpanded = true
            },
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = if (isExpanded) Int.MAX_VALUE else effectiveMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { layoutResult ->
                isOverflowing = !isExpanded && layoutResult.hasVisualOverflow
            },
        )
        if (!isExpanded && isOverflowing) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .wrapContentHeight()
                    .height(IntrinsicSize.Min)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(24.dp)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, backgroundColor)
                            )
                        )
                )
                Text(
                    modifier = Modifier
                        .background(backgroundColor)
                        .padding(start = 4.dp),
                    text = showMoreText,
                    style = style,
                    color = showMoreColor,
                )
            }
        }
    }
}
