package com.tonapps.portfolio.screens.wallet.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.portfolio.screens.wallet.EarnPositionUi
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLabel
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.MoonBundleTitleCell
import ui.components.moon.cell.TextCell
import ui.components.moon.cell.defaultBundleType
import ui.preview.ThemedPreview
import ui.theme.UIKit

private val RowHeight = 76.dp

@Composable
internal fun EarnPositionsSection(
    totalFormat: String,
    positions: List<EarnPositionUi>,
    onOpenEarn: () -> Unit,
    onOpenPosition: (network: String, vaultId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        MoonBundleTitleCell(
            title = stringResource(Localization.earn_positions_title),
            onClick = onOpenEarn,
            content = { SectionBalance(format = totalFormat, onClick = onOpenEarn) },
        )
        positions.forEachIndexed { index, position ->
            EarnPositionRow(
                position = position,
                bundlePosition = defaultBundleType(positions.size, index),
                onClick = { onOpenPosition(position.network, position.vaultId) },
            )
        }
    }
}

@Composable
private fun EarnPositionRow(
    position: EarnPositionUi,
    bundlePosition: MoonBundlePosition,
    onClick: () -> Unit,
) {
    MoonBundleCell(position = bundlePosition) {
        TextCell(
            title = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MoonItemTitle(text = position.assetSymbol)
                    MoonLabel(text = position.networkName)
                }
            },
            subtitle = { MoonItemSubtitle(text = position.protocolName) },
            image = {
                EarnAssetIcon(
                    assetSymbol = position.assetSymbol,
                    assetIconUrl = position.assetIconUrl,
                    networkIcon = position.networkIcon,
                )
            },
            content = {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = position.balanceFormat,
                        style = UIKit.typography.label1,
                        color = UIKit.colorScheme.text.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = position.earnedFormat,
                        style = UIKit.typography.body2,
                        color = UIKit.colorScheme.accent.green,
                        maxLines = 1,
                    )
                }
            },
            onClick = onClick,
            minHeight = RowHeight,
        )
    }
}

@Preview
@Composable
private fun EarnPositionsSectionPreview() {
    ThemedPreview(isDarkOnly = true) {
        EarnPositionsSection(
            totalFormat = "$ 1 230.54",
            positions = listOf(
                EarnPositionUi(
                    network = "mainnet",
                    vaultId = "0x23878914EFE38d27C4D67Ab83ed1b93A74D4086a",
                    assetSymbol = "USDT",
                    assetIconUrl = null,
                    networkIcon = UIKitIcon.ic_eth,
                    networkName = "Ethereum",
                    protocolName = "Morpho",
                    balanceFormat = "$ 120.54",
                    earnedFormat = "+ $ 0.50",
                ),
                EarnPositionUi(
                    network = "mainnet",
                    vaultId = "0x98C23E9d8f34FEFb1B7BD6a91B7FF122F4e16F5c",
                    assetSymbol = "USDT",
                    assetIconUrl = null,
                    networkIcon = UIKitIcon.ic_eth,
                    networkName = "Ethereum",
                    protocolName = "Aave",
                    balanceFormat = "$ 1 110",
                    earnedFormat = "+ $ 20.4",
                ),
            ),
            onOpenEarn = {},
            onOpenPosition = { _, _ -> },
        )
    }
}