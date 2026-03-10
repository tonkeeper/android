package ui.components.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.components.SpoilerParticles
import ui.components.base.SimpleText
import ui.theme.Dimens
import ui.theme.Shapes
import ui.theme.UIKit

@Composable
internal fun EventActionText(
    modifier: Modifier = Modifier,
    state: UiEvent.Item.Action.Text,
    index: Int,
    onClick: (part: EventItemClickPart) -> Unit
) {
    when (state) {
        is UiEvent.Item.Action.Text.Plain -> {
            EventActionPlainText(
                modifier = modifier,
                text = state.text,
                buttonTitle = state.moreButtonText,
            )
        }
        is UiEvent.Item.Action.Text.Encrypted -> {
            EventActionEncryptedText(
                modifier = modifier,
                placeholder = state.placeholder,
                onClick = { onClick(EventItemClickPart.Encrypted(index)) }
            )
        }
    }
}

@Composable
private fun EventActionEncryptedText(
    modifier: Modifier = Modifier,
    placeholder: String,
    onClick: () -> Unit
) {

    Box(
        modifier = modifier
            .clip(Shapes.medium12)
            .clickable(onClick = onClick)
            .background(UIKit.colorScheme.background.contentTint)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        SpoilerParticles(
            modifier = Modifier
                .fillMaxSize()
                .height(24.dp),
            color = UIKit.colorScheme.text.primary
        )
    }

    /*Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .background(UIKit.colorScheme.background.contentTint)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {

        Icon(
            painter = painterResource(Res.drawable.ic_lock_16),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = UIKit.colorScheme.accent.green
        )

        Text(
            text = placeholder,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = UIKit.colorScheme.text.primary,
            style = UIKit.typography.body2,
        )
    }*/
}

@Composable
private fun EventActionPlainText(
    modifier: Modifier = Modifier,
    text: String,
    buttonTitle: String,
) {

    val backgroundColor = UIKit.colorScheme.background.contentTint
    val moreButtonColor = UIKit.colorScheme.accent.blue

    val textStyle = UIKit.typography.body2
    val density = LocalDensity.current

    val shortRadius = 18.dp
    val longRadius = 12.dp

    var overflow by remember { mutableStateOf(false) }
    var lastLineHeightPx by remember { mutableStateOf(0f) }

    Box(
        modifier = modifier
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(if (overflow) longRadius else shortRadius)
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = UIKit.colorScheme.text.primary,
            style = textStyle,
            onTextLayout = { result ->
                val hasVisualOverflow = result.hasVisualOverflow
                if (overflow != hasVisualOverflow) {
                    overflow = hasVisualOverflow
                }
                val idx = if (result.lineCount > 1) 1 else 0
                val lineHeight = (result.getLineBottom(idx) - result.getLineTop(idx))
                if (lineHeight > 0 && lastLineHeightPx != lineHeight) {
                    lastLineHeightPx = lineHeight
                }
            }
        )

        if (overflow && lastLineHeightPx > 0) {
            val lastLineHeightDp = with(density) {
                lastLineHeightPx.toDp()
            }

            EventActionPlainTextMore(
                modifier = Modifier.align(Alignment.BottomEnd),
                buttonTitle = buttonTitle,
                lineHeight = lastLineHeightDp,
                backgroundColor = backgroundColor,
                moreButtonColor = moreButtonColor
            )
        }
    }
}

@Composable
private fun EventActionPlainTextMore(
    modifier: Modifier = Modifier,
    buttonTitle: String,
    lineHeight: Dp,
    backgroundColor: Color,
    moreButtonColor: Color,
) {

    val fadeBrush = remember(backgroundColor) {
        Brush.horizontalGradient(
            0f to Color.Transparent,
            0.5f to backgroundColor.copy(alpha = 0.9f),
            1f to backgroundColor
        )
    }

    Row(
        modifier = modifier.height(lineHeight)
    ) {
        Box(
            modifier = Modifier
                .width(Dimens.cornerMedium)
                .fillMaxHeight()
                .background(brush = fadeBrush)
        )

        Box(
            modifier = Modifier
                .background(backgroundColor)
                .padding(start = 6.dp, end = 2.dp)
                .align(Alignment.CenterVertically)
        ) {
            SimpleText(
                text = buttonTitle,
                color = moreButtonColor,
                style = UIKit.typography.body2
            )
        }
    }
}