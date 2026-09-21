package com.tonapps.swap.screens.picker

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.tonapps.core.components.AccountCell
import com.tonapps.core.components.AccountCellShimmer
import com.tonapps.core.components.ChainFilterBar
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.localization.Localization
import ui.components.moon.cell.MoonSearchCell
import ui.components.moon.cell.defaultBundleType
import ui.components.moon.container.MoonScaffold
import ui.components.moon.screen.MoonEmptyScreen
import ui.components.moon.screen.MoonEmptyScreenType
import ui.theme.UIKit
import ui.workaround.hideKeyboardOnScrollConnection

private const val EMPTY_KEY = 1
private const val SHIMMER_KEY = 2
private const val LOADING_MORE_KEY = 3

@Composable
fun SwapAssetPickerScreen(
    feature: SwapAssetPickerFeature,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onAssetSelected: (AssetEntity) -> Unit,
) {
    val pagingItems = feature.accountsFlow.collectAsLazyPagingItems()
    val searchText = rememberSaveable { mutableStateOf("") }
    val selectedChain by feature.selectedFilter.collectAsState()

    val listState = rememberLazyListState()
    val isLoading = pagingItems.loadState.refresh is LoadState.Loading
    val isEmpty = !isLoading && pagingItems.itemCount == 0
    val showSkeleton = isLoading && pagingItems.itemCount == 0

    LaunchedEffect(selectedChain, searchText.value) {
        listState.scrollToItem(0)
    }

    MoonScaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        title = stringResource(Localization.choose_asset),
        onBack = onBack,
        onClose = onClose,
    ) {
        MoonSearchCell(
            searchText = searchText,
            placeholder = stringResource(Localization.search_by_ticker),
            onChanged = {
                searchText.value = it
                feature.onSearch(it)
            },
        )

        ChainFilterBar(
            selectedNetwork = selectedChain,
            onNetworkSelected = { network ->
                feature.selectFilter(network)
            },
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .nestedScroll(hideKeyboardOnScrollConnection()),
            state = listState,
            contentPadding = PaddingValues(
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
            ),
        ) {
            when {
                showSkeleton -> {
                    val count = 10
                    items(
                        count,
                        key = { "shimmer_$it" },
                        contentType = { SHIMMER_KEY },
                    ) { index ->
                        AccountCellShimmer(
                            position = defaultBundleType(count, index),
                            balances = false,
                        )
                    }
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

                else -> {
                    items(
                        count = pagingItems.itemCount,
                        key = pagingItems.itemKey { it.asset.id },
                        contentType = pagingItems.itemContentType { "account" },
                    ) { index ->
                        val item = pagingItems[index] ?: return@items
                        AccountCell(
                            account = item,
                            position = defaultBundleType(pagingItems.itemCount, index),
                            showVerification = true,
                            onClick = { onAssetSelected(item.asset) },
                            content = {},
                        )
                    }
                    if (pagingItems.loadState.append is LoadState.Loading) {
                        item(LOADING_MORE_KEY) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = UIKit.colorScheme.accent.blue,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
