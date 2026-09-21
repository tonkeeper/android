package com.tonapps.migration.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.core.components.Emoji
import com.tonapps.core.extensions.formatFiat
import com.tonapps.icu.Coins
import com.tonapps.migration.data.MigratableWallet
import com.tonapps.wallet.localization.Plurals
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelDefault
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.TextCell
import ui.theme.UIKit

@Composable
internal fun MigratableWalletCell(
    wallet: MigratableWallet,
    currencyCode: String,
    selected: Boolean,
    position: MoonBundlePosition,
    onClick: () -> Unit,
    showRadio: Boolean = true,
) {
    MoonBundleCell(position = position) {
        TextCell(
            title = wallet.wallet.label.name,
            subtitle = formatSubtitle(
                fiatBalance = wallet.fiatBalance,
                currencyCode = currencyCode,
                nftCount = wallet.nftCount,
            ),
            tags = {
                if (wallet.wallet.isW5) {
                    MoonLabel(
                        text = "W5",
                        colors = MoonLabelDefault.success(),
                    )
                }
            },
            image = {
                WalletEmojiIcon(wallet = wallet.wallet)
            },
            content = if (showRadio) {
                {
                    RadioButton(
                        selected = selected,
                        onClick = onClick,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = UIKit.colorScheme.accent.blue,
                            unselectedColor = UIKit.colorScheme.icon.secondary,
                        ),
                    )
                }
            } else {
                null
            },
            onClick = if (showRadio) {
                onClick
            } else {
                null
            },
            minHeight = 76.dp,
        )
    }
}

@Composable
private fun WalletEmojiIcon(wallet: WalletEntity) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(wallet.label.color)),
        contentAlignment = Alignment.Center,
    ) {
        Emoji(emoji = wallet.label.emoji)
    }
}

@Composable
private fun formatSubtitle(
    fiatBalance: Coins,
    currencyCode: String,
    nftCount: Int,
): String {
    val balance = fiatBalance.formatFiat(currencyCode)
    val nfts = pluralStringResource(Plurals.nft_count, nftCount, nftCount)
    return "$balance · $nfts"
}
