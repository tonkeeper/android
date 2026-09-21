package com.tonapps.core.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import ui.components.moon.MoonItemIcon
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.TextCell
import ui.painterResource
import ui.theme.UIKit

@Composable
fun WalletRowCell(
    wallet: McWalletEntity,
    selected: Boolean,
    position: MoonBundlePosition,
    onClick: () -> Unit,
    subtitle: CharSequence? = null,
) {
    WalletRowCell(
        wallet = wallet,
        content = if (selected) {
            {
                MoonItemIcon(
                    painter = painterResource(UIKitIcon.ic_donemark_otline_28),
                    color = UIKit.colorScheme.accent.blue,
                )
            }
        } else {
            null
        },
        position = position,
        onClick = onClick,
        subtitle = subtitle,
    )
}

@Composable
fun WalletRowCell(
    wallet: McWalletEntity,
    content: (@Composable () -> Unit)? = null,
    position: MoonBundlePosition,
    onClick: () -> Unit,
    subtitle: CharSequence? = null,
) {
    MoonBundleCell(position = position) {
        TextCell(
            title = wallet.name,
            subtitle = subtitle,
            image = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(wallet.color)),
                    contentAlignment = Alignment.Center,
                ) {
                    Emoji(emoji = wallet.emoji)
                }
            },
            content = content,
            onClick = onClick,
            minHeight = 76.dp,
        )
    }
}
