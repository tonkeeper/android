package com.tonapps.core.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.wallet.localization.Localization
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import ui.components.moon.MoonChipBarCell
import ui.components.moon.MoonChipBarCellDefaultPadding
import ui.components.moon.MoonItem

const val ALL_CHAINS_FILTER_ID = -1

@Composable
fun rememberChainFilters(): ImmutableList<MoonItem> {
    val context = LocalContext.current
    val allStr = stringResource(Localization.all)
    return remember(allStr) {
        (listOf(MoonItem(id = ALL_CHAINS_FILTER_ID, title = allStr)) +
            Chain.all.map {
                MoonItem(
                    id = it.network.type.ordinal,
                    title = it.coin.name,
                    image = it.coin.chainImageResourceUrl(context),
                )
            }).toPersistentList()
    }
}

@Composable
fun ChainFilterBar(
    selectedNetwork: Network.Type?,
    onNetworkSelected: (Network.Type?) -> Unit,
    modifier: Modifier = Modifier,
    scrollToSelected: Boolean = false,
    contentPadding: PaddingValues = MoonChipBarCellDefaultPadding,
) {
    val chainFilters = rememberChainFilters()

    MoonChipBarCell(
        modifier = modifier.fillMaxWidth(),
        filters = chainFilters,
        selectedId = selectedNetwork?.ordinal ?: ALL_CHAINS_FILTER_ID,
        scrollToSelected = scrollToSelected,
        contentPadding = contentPadding,
        onSelect = { item ->
            val network = if (item.id == ALL_CHAINS_FILTER_ID) {
                null
            } else {
                Network.Type.entries[item.id]
            }
            onNetworkSelected(network)
        },
    )
}
