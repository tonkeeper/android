package ui.components.moon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ui.theme.UIKit
import ui.theme.modifiers.modifyIf

// TODO combine with chip
@Composable
fun MoonBadgeButton(
    text: String,
    prefixContent: (@Composable () -> Unit)? = null,
    suffixContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    MoonBadgeButton(
        content = {
            prefixContent?.invoke()
            MoonItemTitle(text = text)
            suffixContent?.invoke()
        },
        onClick = onClick,
    )
}

@Composable
fun MoonBadgeButton(
    content: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(UIKit.colorScheme.background.contentTint)
            .padding(8.dp)
            .modifyIf {
                onClick?.let { clickable(onClick = onClick) }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        content()
    }
}