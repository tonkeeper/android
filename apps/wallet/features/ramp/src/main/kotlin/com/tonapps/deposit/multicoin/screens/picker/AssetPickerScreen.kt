package com.tonapps.deposit.multicoin.screens.picker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.core.components.AccountCell
import com.tonapps.core.components.chainImageResourceUrl
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.displayOrder
import com.tonapps.wallet.localization.Localization
import kotlinx.collections.immutable.toPersistentList
import ui.components.moon.MoonChipBarCell
import ui.components.moon.MoonItem
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.MoonSearchCell
import ui.components.moon.container.MoonScaffold
import ui.components.moon.screen.MoonEmptyScreen
import ui.components.moon.screen.MoonEmptyScreenType
import ui.components.moon.screen.MoonLoadingScreen
import ui.workaround.hideKeyboardOnScrollConnection

private const val DEFAULT_SEARCH_KEY = -1
private const val EMPTY_KEY = 3
private const val LOADING_KEY = 4

// Matches the iOS token picker row ids, so one Maestro step picks an asset in both apps.
private val AccountWithDetails.testTag: String
    get() {
        val symbol = asset.symbol.ifEmpty { asset.name }.lowercase().replace(Regex("[^a-z0-9]+"), "_")
        val chain = asset.valueOrNull?.chain?.network?.type ?: return "token_picker_asset_$symbol"
        val code = when (chain) {
            Network.Type.Ethereum -> "eth"
            Network.Type.Arbitrum -> "arb"
            Network.Type.Avalanche -> "avax"
            Network.Type.Bitcoin -> "btc"
            Network.Type.Smartchain -> "bsc"
            Network.Type.Solana -> "sol"
            else -> chain.name.lowercase()
        }
        return "token_picker_asset_${code}_$symbol"
    }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AssetPickerScreen(
    feature: AssetPickerFeature,
    onClose: () -> Unit,
    onAssetSelected: (AccountWithDetails) -> Unit,
    group: Network.Group? = null,
) {
    val pagingItems = feature.accountsFlow.collectAsLazyPagingItems()
    val searchText = rememberSaveable { mutableStateOf("") }

    val isLoading = pagingItems.loadState.refresh is LoadState.Loading
    val isEmpty = !isLoading && pagingItems.itemCount == 0

    MoonScaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        title = stringResource(Localization.choose_asset),
        onClose = onClose,
    ) {
        MoonSearchCell(
            placeholder = stringResource(Localization.search),
            searchText = searchText,
            onChanged = {
                searchText.value = it
                feature.onSearch(it)
            },
        )

        val selectedFilter = feature.selectedFilter.collectAsState()
        val context = LocalContext.current
        val allStr = stringResource(Localization.all)
        val chains = remember(group) {
            Chain.displayOrder.filter { group == null || it.network.group == group }
        }
        val coins = remember(group) {
            val head = if (group == null) {
                listOf(MoonItem(id = DEFAULT_SEARCH_KEY, title = allStr))
            } else {
                emptyList()
            }
            (head + chains.map {
                MoonItem(id = it.network.type.ordinal, title = it.coin.name, image = it.coin.chainImageResourceUrl(context))
            }).toPersistentList()
        }

        // A scanned address scopes the picker to its network group: preselect the first chain so paging is filtered to it.
        LaunchedEffect(group) {
            if (group != null) {
                chains.firstOrNull()?.let { feature.selectFilter(it.network.type) }
            }
        }

        MoonChipBarCell(
            filters = coins,
            selectedId = selectedFilter.value?.ordinal ?: DEFAULT_SEARCH_KEY,
            onSelect = {
                if (it.id < 0) {
                    feature.selectFilter(null)
                } else {
                    feature.selectFilter(Network.Type.entries[it.id])
                }
            }
        )

        LazyColumn(
            modifier = Modifier.semantics { testTagsAsResourceId = true }
                .nestedScroll(hideKeyboardOnScrollConnection())
                .nestedScroll(rememberNestedScrollInteropConnection()),
            contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
            ),
        ) {
            when {
                isLoading -> item(key = LOADING_KEY, contentType = { LOADING_KEY }) {
                    MoonLoadingScreen()
                }
                isEmpty -> item(key = EMPTY_KEY, contentType = { EMPTY_KEY }) {
                    MoonEmptyScreen(
                        text = stringResource(Localization.cant_find_anything),
                        description = if (searchText.value.isEmpty()) {
                            stringResource(Localization.no_results)
                        } else {
                            stringResource(Localization.search_no_result, searchText.value)
                        },
                        type = MoonEmptyScreenType.Empty,
                    )
                }
                else -> items(
                    count = pagingItems.itemCount,
                    key = pagingItems.itemKey { it.account.asset.id },
                    contentType = pagingItems.itemContentType { "account" },
                ) { index ->
                    val item = pagingItems[index] ?: return@items
                    Box(modifier = Modifier.testTag(item.account.testTag)) {
                        AccountCell(
                            account = item.account,
                            position = MoonBundlePosition.default(pagingItems.itemCount, index),
                            hiddenBalance = item.hiddenBalance,
                            onClick = { onAssetSelected(item.account) },
                            content = {}
                        )
                    }
                }
            }
        }
    }
}
