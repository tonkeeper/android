package com.tonapps.deposit.multicoin.screens.assets

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.core.components.AccountCell
import com.tonapps.core.components.chainImageResourceUrl
import com.tonapps.core.components.imageResourceUrl
import com.tonapps.deposit.multicoin.data.RampAsset
import com.tonapps.wallet.localization.Localization
import kotlinx.collections.immutable.toPersistentList
import ui.components.moon.MoonChipBarCell
import ui.components.moon.MoonItem
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonItemTitle
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.MoonSearchCell
import ui.components.moon.container.MoonScaffold
import ui.components.moon.screen.MoonEmptyScreen
import ui.components.moon.screen.MoonEmptyScreenType
import ui.components.moon.screen.MoonLoadingScreen
import ui.workaround.hideKeyboardOnScrollConnection

private const val EMPTY_KEY = "empty"
private const val LOADING_KEY = "loading"
private const val ALL_FILTER_KEY = -1

// TODO combine with AssetPickerFeature
@Composable
fun AssetsExtendedScreen(
    feature: AssetsExtendedFeature,
    title: String = stringResource(Localization.choose_asset),
    onClose: () -> Unit,
    onBack: () -> Unit,
    onSelected: (RampAsset) -> Unit,
) {
    val pagingItems = feature.assetsFlow.collectAsLazyPagingItems()
    val searchText = rememberSaveable { mutableStateOf("") }

    val isLoading = pagingItems.loadState.refresh is LoadState.Loading
    val isEmpty = !isLoading && pagingItems.itemCount == 0

    MoonScaffold(
        modifier = Modifier.imePadding(),
        title = title,
        onClose = onClose,
        onBack = onBack,
    ) {
        MoonSearchCell(
            searchText = searchText,
            onChanged = {
                searchText.value = it
                feature.onSearch(it)
            },
            error = false,
        )

        val selectedFilter = feature.selectedFilter.collectAsState()
        val chainFilters = feature.chainFilters.collectAsState()
        val context = LocalContext.current
        val allStr = stringResource(Localization.all)
        val coins = remember(chainFilters.value) {
            val data = listOf(MoonItem(id = ALL_FILTER_KEY, title = allStr)) + chainFilters.value.map {
                MoonItem(
                    id = it.network.type.ordinal,
                    title = it.coin.name,
                    image = it.coin.chainImageResourceUrl(context),
                )
            }

            data.toPersistentList()
        }

        MoonChipBarCell(
            filters = coins,
            selectedId = selectedFilter.value?.ordinal ?: ALL_FILTER_KEY,
            onSelect = {
                if (it.id < 0) {
                    feature.selectFilter(null)
                } else {
                    feature.selectFilter(Network.Type.entries[it.id])
                }
            }
        )

        LazyColumn(
            modifier = Modifier
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
                    val query = searchText.value
                    MoonEmptyScreen(
                        text = stringResource(Localization.cant_find_anything),
                        description = if (query.isEmpty()) "There were no results" else "There were no results for '$query'",
                        type = MoonEmptyScreenType.Empty,
                    )
                }
                else -> items(
                    count = pagingItems.itemCount,
                    key = pagingItems.itemKey { it.asset.id },
                    contentType = pagingItems.itemContentType { "asset" },
                ) { index ->
                    val asset = pagingItems[index] ?: return@items
                    RampAssetRow(
                        asset = asset,
                        position = MoonBundlePosition.default(pagingItems.itemCount, index),
                        onClick = { onSelected(asset) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RampAssetRow(
    asset: RampAsset,
    position: MoonBundlePosition,
    onClick: () -> Unit,
) {
    AccountCell(
        asset = asset.asset,
        title = { MoonItemTitle(text = asset.asset.symbol) },
        subtitle = {
            asset.networkName?.let { MoonItemSubtitle(text = it) }
        },
        position = position,
        onClick = onClick,
    )
}
