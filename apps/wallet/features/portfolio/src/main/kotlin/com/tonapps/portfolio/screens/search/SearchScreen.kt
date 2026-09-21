package com.tonapps.portfolio.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.tonapps.core.components.AccountCellShimmer
import com.tonapps.core.components.ChainFilterBar
import com.tonapps.core.components.SearchAssetCell
import com.tonapps.perps.data.CatalogSearchRow
import com.tonapps.perps.data.CatalogSearchSort
import com.tonapps.perps.data.PerpsLoadException
import com.tonapps.perps.data.PerpsSort
import com.tonapps.perps.screens.markets.PerpMarketRow
import com.tonapps.perps.screens.markets.perpsErrorDescription
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonActionSelector
import ui.components.moon.cell.MoonRetryCell
import ui.components.moon.cell.MoonSearchCell
import ui.components.moon.cell.defaultBundleType
import ui.components.moon.container.MoonScaffold
import ui.components.moon.screen.MoonEmptyScreen
import ui.components.moon.screen.MoonEmptyScreenType
import ui.theme.Dimens
import ui.theme.UIKit
import ui.workaround.hideKeyboardOnScrollConnection
private const val EMPTY_KEY = 1
private const val SHIMMER_KEY = 2
private const val LOADING_MORE_KEY = 3
private const val ERROR_KEY = 4
private const val APPEND_ERROR_KEY = 5
private const val SEARCH_ASSET_TYPE = "search_asset"
private const val PERP_ROW_TYPE = "perp_row"

@Composable
fun SearchScreen(
    feature: SearchFeature,
    onBack: () -> Unit,
    onOpenAsset: (AssetEntity) -> Unit = {},
    onOpenPerpMarket: (Int, String) -> Unit,
) {
    val sortOrder = feature.sortOrder.collectAsState()
    val perpsSort = feature.perpsSort.collectAsState()
    val searchText = rememberSaveable { mutableStateOf("") }
    val selectedChain = feature.networkFilter.collectAsState()
    val assetType = feature.assetType.collectAsState()
    val isPerpsVisible = feature.isPerpsVisible.collectAsState()

    val scrollConnection = hideKeyboardOnScrollConnection()
    val showPerpsResults = isPerpsVisible.value && assetType.value == SearchAssetType.PERPETUALS

    MoonScaffold(
        modifier = Modifier
            .nestedScroll(rememberNestedScrollInteropConnection())
            .statusBarsPadding(),
        title = stringResource(Localization.crypto),
        onBack = onBack,
    ) {
        MoonSearchCell(
            searchText = searchText,
            placeholder = stringResource(Localization.search_by_ticker),
            onChanged = {
                searchText.value = it
                feature.onSearch(it)
            },
        )

        if (showPerpsResults) {
            Spacer(modifier = Modifier.height(Dimens.offsetMedium))
        } else {
            ChainFilterBar(
                selectedNetwork = selectedChain.value,
                onNetworkSelected = { network ->
                    feature.onNetworkSelected(network)
                },
                scrollToSelected = true,
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (showPerpsResults) {
                PerpsResults(
                    feature = feature,
                    searchText = searchText.value,
                    scrollConnection = scrollConnection,
                    onOpenPerpMarket = onOpenPerpMarket,
                )
            } else {
                SpotResults(
                    feature = feature,
                    searchText = searchText.value,
                    scrollConnection = scrollConnection,
                    onOpenAsset = onOpenAsset,
                    onOpenPerpMarket = onOpenPerpMarket,
                )
            }
            SearchBottomSelectors(
                modifier = Modifier.align(Alignment.BottomCenter),
                selectedSort = sortOrder.value,
                onSortSelect = { feature.onSortSelected(sort = it) },
                selectedPerpsSort = perpsSort.value,
                onPerpsSortSelect = { feature.onPerpsSortSelected(it) },
                isPerpsVisible = isPerpsVisible.value,
                selectedAssetType = assetType.value,
                onAssetTypeSelect = { feature.onAssetTypeSelected(it) },
            )
        }
    }
}

@Composable
private fun SpotResults(
    feature: SearchFeature,
    searchText: String,
    scrollConnection: NestedScrollConnection,
    onOpenAsset: (AssetEntity) -> Unit,
    onOpenPerpMarket: (Int, String) -> Unit,
) {
    val pagingItems = feature.assetsFlow.collectAsLazyPagingItems()
    val sortOrder = feature.sortOrder.collectAsState()
    val debouncedQuery = feature.debouncedQuery.collectAsState()
    val selectedChain = feature.networkFilter.collectAsState()
    val assetType = feature.assetType.collectAsState()

    val listState = rememberLazyListState()
    val refresh = pagingItems.loadState.refresh
    val append = pagingItems.loadState.append
    val isLoading = refresh is LoadState.Loading || (pagingItems.itemCount == 0 && append is LoadState.Loading)
    val isError = pagingItems.itemCount == 0 && (refresh is LoadState.Error || append is LoadState.Error)
    val isEmpty = !isLoading && !isError && pagingItems.itemCount == 0
    val showSkeleton = isLoading && pagingItems.itemCount == 0

    var scrollToTopPending by remember { mutableStateOf(false) }
    var filtersSeen by remember { mutableStateOf(false) }
    LaunchedEffect(selectedChain.value, sortOrder.value, debouncedQuery.value, assetType.value) {
        if (filtersSeen) {
            scrollToTopPending = true
        } else {
            filtersSeen = true
        }
    }
    LaunchedEffect(refresh, scrollToTopPending) {
        if (scrollToTopPending && refresh is LoadState.NotLoading) {
            listState.scrollToItem(0)
            scrollToTopPending = false
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollConnection),
        contentPadding = PaddingValues(
            bottom = navBarPadding.calculateBottomPadding() + Dimens.heightBar
        ),
        state = listState,
    ) {
        when {
            showSkeleton -> {
                val count = 10
                items(
                    count,
                    key = { "shimmer_$it" },
                    contentType = { SHIMMER_KEY }) { index ->
                    AccountCellShimmer(
                        position = defaultBundleType(count, index),
                    )
                }
            }

            isError -> item(key = ERROR_KEY, contentType = { ERROR_KEY }) {
                MoonRetryCell(
                    message = stringResource(Localization.something_went_wrong),
                    buttonText = stringResource(Localization.retry),
                    onRetry = pagingItems::retry,
                )
            }

            isEmpty -> item(key = EMPTY_KEY, contentType = { EMPTY_KEY }) {
                SearchEmpty(searchText = searchText)
            }

            else -> {
                items(
                    count = pagingItems.itemCount,
                    key = pagingItems.itemKey { it.id },
                    contentType = pagingItems.itemContentType { item ->
                        if (item is CatalogSearchRow.Perp) {
                            PERP_ROW_TYPE
                        } else {
                            SEARCH_ASSET_TYPE
                        }
                    },
                ) { index ->
                    when (val row = pagingItems[index] ?: return@items) {
                        is CatalogSearchRow.Perp -> PerpMarketRow(
                            market = row.market,
                            position = defaultBundleType(pagingItems.itemCount, index),
                            currencyCode = row.currencyCode,
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onOpenPerpMarket(row.market.marketIndex, row.market.symbol)
                            },
                            onVisibilityChange = {},
                        )

                        is CatalogSearchRow.Spot -> SearchAssetCell(
                            item = row.item,
                            position = defaultBundleType(pagingItems.itemCount, index),
                            onClick = {
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                onOpenAsset(row.item.asset)
                            },
                        )
                    }
                }
                if (append is LoadState.Loading) {
                    item(LOADING_MORE_KEY) {
                        SearchAppendLoader()
                    }
                }
                if (append is LoadState.Error) {
                    item(APPEND_ERROR_KEY) {
                        MoonRetryCell(
                            message = stringResource(Localization.something_went_wrong),
                            buttonText = stringResource(Localization.retry),
                            onRetry = pagingItems::retry,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PerpsResults(
    feature: SearchFeature,
    searchText: String,
    scrollConnection: NestedScrollConnection,
    onOpenPerpMarket: (Int, String) -> Unit,
) {
    val pagingItems = feature.perpsFlow.collectAsLazyPagingItems()
    val perpsSort = feature.perpsSort.collectAsState()
    val debouncedQuery = feature.debouncedQuery.collectAsState()
    val perpsCurrency = feature.perpsCurrency.collectAsState()

    val listState = rememberLazyListState()
    val refresh = pagingItems.loadState.refresh
    val append = pagingItems.loadState.append
    val isLoading = refresh is LoadState.Loading || (pagingItems.itemCount == 0 && append is LoadState.Loading)
    val showSkeleton = isLoading && pagingItems.itemCount == 0
    val isError = pagingItems.itemCount == 0 && (refresh is LoadState.Error || append is LoadState.Error)
    val isEmpty = !isLoading && !isError && pagingItems.itemCount == 0

    var scrollToTopPending by remember { mutableStateOf(false) }
    var filtersSeen by remember { mutableStateOf(false) }
    LaunchedEffect(perpsSort.value, debouncedQuery.value) {
        if (filtersSeen) {
            scrollToTopPending = true
        } else {
            filtersSeen = true
        }
    }
    LaunchedEffect(refresh, scrollToTopPending) {
        if (scrollToTopPending && refresh is LoadState.NotLoading) {
            listState.scrollToItem(0)
            scrollToTopPending = false
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollConnection),
        contentPadding = PaddingValues(
            bottom = navBarPadding.calculateBottomPadding() + Dimens.heightBar
        ),
        state = listState,
    ) {
        when {
            showSkeleton -> {
                val count = 10
                items(
                    count,
                    key = { "shimmer_$it" },
                    contentType = { SHIMMER_KEY }) { index ->
                    AccountCellShimmer(
                        position = defaultBundleType(count, index),
                    )
                }
            }

            isError -> item(key = ERROR_KEY, contentType = { ERROR_KEY }) {
                val failure = refresh as? LoadState.Error ?: append as? LoadState.Error
                val error = failure?.error as? PerpsLoadException
                MoonRetryCell(
                    message = perpsErrorDescription(error?.error)
                        ?: stringResource(Localization.perps_markets_load_failed),
                    buttonText = stringResource(Localization.perps_retry),
                    onRetry = pagingItems::retry,
                )
            }

            isEmpty -> item(key = EMPTY_KEY, contentType = { EMPTY_KEY }) {
                SearchEmpty(searchText = searchText)
            }

            else -> {
                items(
                    count = pagingItems.itemCount,
                    key = pagingItems.itemKey { "perp_${it.marketIndex}" },
                    contentType = { PERP_ROW_TYPE },
                ) { index ->
                    val market = pagingItems[index] ?: return@items
                    PerpMarketRow(
                        market = market,
                        position = defaultBundleType(pagingItems.itemCount, index),
                        currencyCode = perpsCurrency.value,
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onOpenPerpMarket(market.marketIndex, market.symbol)
                        },
                        onVisibilityChange = {},
                    )
                }
                if (pagingItems.loadState.append is LoadState.Loading) {
                    item(LOADING_MORE_KEY) {
                        SearchAppendLoader()
                    }
                }
                if (pagingItems.loadState.append is LoadState.Error) {
                    item(APPEND_ERROR_KEY) {
                        MoonRetryCell(
                            message = stringResource(Localization.perps_markets_load_failed),
                            buttonText = stringResource(Localization.perps_retry),
                            onRetry = pagingItems::retry,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEmpty(searchText: String) {
    MoonEmptyScreen(
        text = stringResource(Localization.cant_find_anything),
        description = if (searchText.isEmpty()) {
            stringResource(Localization.no_results)
        } else {
            stringResource(Localization.search_no_result, searchText)
        },
        type = MoonEmptyScreenType.Empty,
    )
}

@Composable
private fun SearchAppendLoader() {
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

@Composable
private fun SearchBottomSelectors(
    modifier: Modifier = Modifier,
    selectedSort: CatalogSearchSort,
    onSortSelect: (CatalogSearchSort) -> Unit,
    selectedPerpsSort: PerpsSort,
    onPerpsSortSelect: (PerpsSort) -> Unit,
    isPerpsVisible: Boolean,
    selectedAssetType: SearchAssetType,
    onAssetTypeSelect: (SearchAssetType) -> Unit,
) {
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()
    val entries = CatalogSearchSort.entries
    val assetTypes = SearchAssetType.entries

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.heightBar + navBarPadding.calculateBottomPadding())
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        UIKit.colorScheme.background.page
                    )
                )
            )
            .padding(bottom = navBarPadding.calculateBottomPadding()),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isPerpsVisible && selectedAssetType == SearchAssetType.PERPETUALS) {
                val perpsSorts = PerpsSort.entries
                MoonActionSelector(
                    items = listOf(
                        stringResource(Localization.perps_volume),
                        stringResource(Localization.perps_price_change),
                        stringResource(Localization.perps_open_interest),
                    ),
                    selectedIndex = selectedPerpsSort.ordinal,
                    onSelect = { onPerpsSortSelect(perpsSorts[it]) },
                )
            } else {
                MoonActionSelector(
                    items = entries.map { it.displayName() }, // TODO TK-2103 move to feature
                    selectedIndex = entries.indexOf(selectedSort),
                    onSelect = { onSortSelect(entries[it]) },
                )
            }
            if (isPerpsVisible) {
                MoonActionSelector(
                    items = listOf(
                        stringResource(Localization.all_assets),
                        stringResource(Localization.tokens),
                        stringResource(Localization.perps_filter_stocks),
                        stringResource(Localization.perps_filter_etfs),
                        stringResource(Localization.perps_title),
                    ),
                    selectedIndex = selectedAssetType.ordinal,
                    onSelect = { onAssetTypeSelect(assetTypes[it]) },
                )
            }
        }
    }
}