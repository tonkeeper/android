package com.tonapps.core.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.wallet.localization.Localization
import kotlinx.collections.immutable.ImmutableList
import ui.components.moon.MoonActionSelector
import ui.components.moon.MoonActionSelectorStyle
import ui.components.popup.ActionMenuHorizontalAlignment
import ui.preview.ThemedPreview

@Composable
fun ChainFilterSelector(
    selectedNetwork: Network.Type?,
    onNetworkSelected: (Network.Type?) -> Unit,
    modifier: Modifier = Modifier,
    availableNetworks: ImmutableList<Network.Type>? = null,
) {
    val chainFilters = rememberChainFilters(availableNetworks)
    val selectedId = selectedNetwork?.ordinal ?: ALL_CHAINS_FILTER_ID
    val selectedIndex = chainFilters.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    val allChainsLabel = stringResource(Localization.all_chains)

    MoonActionSelector(
        modifier = modifier,
        items = chainFilters.map { it.title },
        itemImages = chainFilters.map { it.image },
        selectedIndex = selectedIndex,
        label = if (selectedIndex == 0) {
            allChainsLabel
        } else {
            null
        },
        style = MoonActionSelectorStyle.Tertiary,
        menuAlignment = ActionMenuHorizontalAlignment.Center,
        menuOffset = DpOffset(0.dp, 4.dp),
        onSelect = { index ->
            val id = chainFilters[index].id
            val network = if (id == ALL_CHAINS_FILTER_ID) {
                null
            } else {
                Network.Type.entries[id]
            }
            onNetworkSelected(network)
        },
    )
}

@Preview
@Composable
private fun ChainFilterSelectorPreview() {
    ThemedPreview(isDarkOnly = true) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChainFilterSelector(
                selectedNetwork = null,
                onNetworkSelected = {},
            )
            ChainFilterSelector(
                selectedNetwork = Chain.Ethereum.Mainnet.network.type,
                onNetworkSelected = {},
            )
        }
    }
}
