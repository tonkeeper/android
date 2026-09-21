package ui.components.moon

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
    onClick: (() -> Unit)? = null,
) {
    MoonBadgeButton(
        content = {
            prefixContent?.invoke()
            MoonSmallItemTitle(text = text)
            suffixContent?.invoke()
        },
        contentPadding = contentPadding,
        onClick = onClick,
    )
}

@Composable
fun MoonBadgeButton(
    content: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(vertical = 8.dp, horizontal = 16.dp),
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(UIKit.colorScheme.background.contentTint)
            .modifyIf {
                onClick?.let { clickable(onClick = onClick) }
            }
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        content()
    }
}