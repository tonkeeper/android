package com.tonapps.portfolio.screens.wallet.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.portfolio.screens.wallet.EarnPositionUi
import com.tonapps.portfolio.wallet.WalletSectionLimit
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonActionIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.MoonLabel
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.MoonBundleTitleCell
import ui.components.moon.cell.TextCell
import ui.components.moon.cell.defaultBundleType
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit

private val RowHeight = 76.dp
private const val RETRY_ICON_BACKGROUND_ALPHA = 0.12f

@Composable
internal fun EarnPositionsSection(
    totalFormat: String,
    positions: List<EarnPositionUi>,
    hiddenPositions: List<EarnPositionUi>,
    isStale: Boolean,
    limit: WalletSectionLimit,
    onOpenEarn: () -> Unit,
    onOpenPosition: (network: String, vaultId: String) -> Unit,
    onRetry: () -> Unit,
    onLimitSelect: (WalletSectionLimit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var rows = positions.size
    if (hiddenPositions.isNotEmpty()) {
        rows++
    }
    if (isStale) {
        rows++
    }
    Column(modifier = modifier.fillMaxWidth()) {
        MoonBundleTitleCell(
            title = stringResource(Localization.earn_positions_title),
            onClick = onOpenEarn,
            content = {
                SectionBalance(
                    format = totalFormat,
                    onClick = onOpenEarn,
                    color = if (isStale) {
                        UIKit.colorScheme.text.tertiary
                    } else {
                        UIKit.colorScheme.text.secondary
                    },
                )
            },
        )
        positions.forEachIndexed { index, position ->
            EarnPositionRow(
                position = position,
                bundlePosition = defaultBundleType(rows, index),
                onClick = { onOpenPosition(position.network, position.vaultId) },
            )
        }
        if (hiddenPositions.isNotEmpty()) {
            val moreImages = remember(hiddenPositions) {
                hiddenPositions.map { position ->
                    SectionMoreImage.Remote(url = position.assetIconUrl, preview = position.assetSymbol)
                }
            }
            SectionMoreCell(
                title = stringResource(Localization.earn_more_positions),
                images = moreImages,
                limit = limit,
                limitTestTag = "wallet_earn_limit",
                position = defaultBundleType(rows, positions.size),
                onClick = onOpenEarn,
                onLimitSelect = onLimitSelect,
            )
        }
        if (isStale) {
            EarnRetryRow(
                title = stringResource(Localization.earn_positions_not_updated),
                bundlePosition = defaultBundleType(rows, rows - 1),
                onRetry = onRetry,
            )
        }
    }
}

@Composable
internal fun EarnPositionsUnavailableSection(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        MoonBundleTitleCell(title = stringResource(Localization.earn_positions_title))
        EarnRetryRow(
            title = stringResource(Localization.earn_positions_unavailable),
            bundlePosition = MoonBundlePosition.Default,
            onRetry = onRetry,
        )
    }
}

@Composable
private fun EarnRetryRow(
    title: String,
    bundlePosition: MoonBundlePosition,
    onRetry: () -> Unit,
) {
    MoonBundleCell(position = bundlePosition) {
        TextCell(
            title = { MoonItemTitle(text = title) },
            subtitle = { MoonItemSubtitle(text = stringResource(Localization.earn_positions_tap_to_retry)) },
            image = {
                MoonActionIcon(
                    painter = painterResource(UIKitIcon.ic_exclamationmark_circle_28),
                    onClick = null,
                    tintColor = UIKit.colorScheme.accent.orange,
                    size = EarnAssetIconSize,
                    backgroundColor = UIKit.colorScheme.accent.orange.copy(alpha = RETRY_ICON_BACKGROUND_ALPHA),
                )
            },
            onClick = onRetry,
            minHeight = RowHeight,
        )
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
                    position.earnedFormat?.let { earned ->
                        Text(
                            text = earned,
                            style = UIKit.typography.body2,
                            color = if (position.isProfit) {
                                UIKit.colorScheme.accent.green
                            } else {
                                UIKit.colorScheme.accent.red
                            },
                            maxLines = 1,
                        )
                    }
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
                    earnedFormat = "+ $ 0.5",
                    isProfit = true,
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
                    earnedFormat = "- $ 0.04",
                    isProfit = false,
                ),
            ),
            hiddenPositions = listOf(
                EarnPositionUi(
                    network = "mainnet",
                    vaultId = "0x8eB67A509616cd6A7c1B3c8C21D48FF57df3d458",
                    assetSymbol = "USDC",
                    assetIconUrl = null,
                    networkIcon = UIKitIcon.ic_eth,
                    networkName = "Ethereum",
                    protocolName = "Compound",
                    balanceFormat = "$ 540.20",
                    earnedFormat = "+ $ 8.12",
                    isProfit = true,
                ),
            ),
            isStale = true,
            limit = WalletSectionLimit.Top3,
            onOpenEarn = {},
            onOpenPosition = { _, _ -> },
            onRetry = {},
            onLimitSelect = {},
        )
    }
}

@Preview
@Composable
private fun EarnPositionsUnavailableSectionPreview() {
    ThemedPreview(isDarkOnly = true) {
        EarnPositionsUnavailableSection(onRetry = {})
    }
}
