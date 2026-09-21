package com.tonapps.portfolio.screens.wallet.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonapps.core.components.Emoji
import com.tonapps.uikit.icon.UIKitIcon
import ui.painterResource
import ui.theme.Shapes
import ui.theme.UIKit

@Composable
internal fun WalletTopBarSwitch(
    modifier: Modifier = Modifier,
    name: String,
    emoji: CharSequence,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(Shapes.large)
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Emoji(emoji = emoji)
        Text(
            text = name,
            style = UIKit.typography.label2,
            color = UIKit.colorScheme.text.primary,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
        Icon(
            modifier = Modifier.alpha(0.64f),
            painter = painterResource(UIKitIcon.ic_chevron_down_16),
            tint = UIKit.colorScheme.icon.primary,
            contentDescription = null,
        )
    }
}
