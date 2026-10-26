package com.tonapps.portfolio.screens.wallet.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import ui.painterResource
import ui.theme.UIKit

@Composable
internal fun SectionBalance(
    format: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = format,
            style = UIKit.typography.label2,
            color = UIKit.colorScheme.text.secondary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            modifier = Modifier.size(16.dp),
            painter = painterResource(UIKitIcon.ic_wallet_outline_16),
            tint = UIKit.colorScheme.icon.secondary,
            contentDescription = null,
        )
    }
}