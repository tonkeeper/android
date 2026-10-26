package com.tonapps.portfolio.screens.wallet.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.portfolio.screens.wallet.EarnVaultUi
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.cell.MoonBundleTitleCell
import ui.preview.ThemedPreview
import ui.theme.Shapes
import ui.theme.UIKit

private val SectionHorizontalPadding = 16.dp
private val ItemSpacing = 8.dp
private val CardWidth = 100.dp

@Composable
internal fun EarnSection(
    vaults: List<EarnVaultUi>,
    onOpenEarn: () -> Unit,
    onOpenVault: (network: String, vaultId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        MoonBundleTitleCell(
            title = stringResource(Localization.earn_title),
            onClick = onOpenEarn,
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = SectionHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            items(items = vaults, key = { it.id }) { vault ->
                EarnVaultCard(
                    vault = vault,
                    onClick = { onOpenVault(vault.network, vault.vaultId) },
                )
            }
        }
    }
}

@Composable
private fun EarnVaultCard(
    vault: EarnVaultUi,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(CardWidth)
            .clip(Shapes.medium)
            .background(UIKit.colorScheme.background.content)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, top = 16.dp, end = 10.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EarnAssetIcon(
            assetSymbol = vault.assetSymbol,
            assetIconUrl = vault.assetIconUrl,
            networkIcon = vault.networkIcon,
        )
        Spacer(modifier = Modifier.height(10.dp))
        EarnVaultCardText(
            text = vault.assetSymbol,
            color = UIKit.colorScheme.text.primary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        EarnVaultCardText(
            text = stringResource(Localization.earn_vault_apy, vault.apyFormat),
            color = UIKit.colorScheme.accent.green,
        )
        Spacer(modifier = Modifier.height(1.dp))
        EarnVaultCardText(
            text = vault.protocolName,
            color = UIKit.colorScheme.text.secondary,
        )
    }
}

@Composable
private fun EarnVaultCardText(
    text: String,
    color: Color,
) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        text = text,
        style = UIKit.typography.body3,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Preview
@Composable
private fun EarnSectionPreview() {
    ThemedPreview(isDarkOnly = true) {
        EarnSection(
            vaults = listOf(
                EarnVaultUi(
                    network = "mainnet",
                    vaultId = "0x23878914EFE38d27C4D67Ab83ed1b93A74D4086a",
                    assetSymbol = "USDC",
                    assetIconUrl = null,
                    networkIcon = UIKitIcon.ic_eth,
                    apyFormat = "6.74%",
                    protocolName = "Aave",
                ),
                EarnVaultUi(
                    network = "base",
                    vaultId = "0xc1256Ae5FF1cf2719D4937adb3bbCCab2E00A2Ca",
                    assetSymbol = "USD₮",
                    assetIconUrl = null,
                    networkIcon = UIKitIcon.ic_base,
                    apyFormat = "3.58%",
                    protocolName = "Morpho",
                ),
                EarnVaultUi(
                    network = "arbitrum",
                    vaultId = "0x724dc807b04555b71ed48a6896b6F41593b8C637",
                    assetSymbol = "ETH",
                    assetIconUrl = null,
                    networkIcon = UIKitIcon.ic_arb,
                    apyFormat = "3.41%",
                    protocolName = "Compound",
                ),
                EarnVaultUi(
                    network = "bsc",
                    vaultId = "0xfD5840Cd36d94D7229439859C0112a4185BC0255",
                    assetSymbol = "USD₮",
                    assetIconUrl = null,
                    networkIcon = UIKitIcon.ic_bsc,
                    apyFormat = "3.56%",
                    protocolName = "Venus",
                ),
            ),
            onOpenEarn = {},
            onOpenVault = { _, _ -> },
        )
    }
}