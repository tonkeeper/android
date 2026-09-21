package com.tonapps.trading.screens.assets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.mvi.props.observeSafeState
import com.tonapps.wallet.localization.Localization
import io.tradingapi.models.AssetRefSummary
import io.tradingapi.models.AssetsTab
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import ui.components.moon.MoonChipBar
import ui.components.moon.MoonDivider
import ui.components.moon.MoonItem
import ui.components.moon.MoonItemDivider
import ui.components.moon.cell.MoonSearchCell
import ui.components.moon.cell.defaultBundleType
import ui.components.moon.container.MoonScaffold
import ui.components.moon.screen.MoonEmptyScreen
import ui.components.moon.screen.MoonEmptyScreenType
import ui.preview.ThemedPreview
import ui.theme.UIKit

private const val LOAD_MORE_THRESHOLD = 5
private const val SEARCH_DEBOUNCE_MS = 300L

@Composable
fun AssetsScreen(
    feature: AssetsFeature,
    focusSearch: Boolean,
    initialTab: AssetsTab,
    onOpenAssetDetails: (AssetItem) -> Unit,
    onBack: () -> Unit,
) {
    val state by feature.state.global.observeSafeState()

    AssetsContent(
        state = state,
        focusSearch = focusSearch,
        initialTab = initialTab,
        onSearch = { query, tab -> feature.sendAction(AssetsAction.Search(query, tab)) },
        onLoadMore = { feature.sendAction(AssetsAction.LoadMore) },
        onAssetClick = {
            feature.trackAssetClick(it)
            onOpenAssetDetails(it)
        },
        onBack = onBack,
    )
}

@Composable
private fun AssetsContent(
    state: AssetsState,
    focusSearch: Boolean,
    initialTab: AssetsTab,
    onSearch: (String, AssetsTab) -> Unit,
    onLoadMore: () -> Unit,
    onAssetClick: (AssetItem) -> Unit,
    onBack: () -> Unit,
) {
    val searchText = rememberSaveable { mutableStateOf("") }
    val selectedTab = rememberSaveable { mutableStateOf(initialTab) }
    var searchInitialized by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val isListScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }

    LaunchedEffect(searchText.value, selectedTab.value) {
        if (!searchInitialized) {
            searchInitialized = true
            return@LaunchedEffect
        }
        delay(SEARCH_DEBOUNCE_MS)
        onSearch(searchText.value.trim(), selectedTab.value)
    }

    LaunchedEffect(selectedTab.value) {
        listState.scrollToItem(0)
    }

    val tabTitles = mapOf(
        AssetsTab.all to stringResource(Localization.tab_all),
        AssetsTab.tokens to stringResource(Localization.tab_tokens),
        AssetsTab.stocks to stringResource(Localization.tab_stocks),
        AssetsTab.etfs to stringResource(Localization.tab_etfs),
    )
    val tabs = remember(tabTitles) {
        tabTitles.map { (tab, title) -> MoonItem(id = tab.ordinal, title = title) }.toImmutableList()
    }

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
            onChanged = { searchText.value = it },
            isFocusOnStart = focusSearch,
        )
        Spacer(Modifier.height(8.dp))
        MoonChipBar(
            modifier = Modifier.padding(vertical = 8.dp),
            filters = tabs,
            selectedId = selectedTab.value.ordinal,
            onSelect = {
                selectedTab.value = AssetsTab.entries[it.id]
            },
        )
        Spacer(Modifier.height(8.dp))
        if (isListScrolled) {
            MoonDivider()
        }
        when (state) {
            AssetsState.Loading -> AssetsShimmer()
            AssetsState.Error -> MoonEmptyScreen(
                type = MoonEmptyScreenType.Error,
                text = stringResource(Localization.something_went_wrong),
                description = stringResource(Localization.could_not_load_content),
                buttonText = stringResource(Localization.retry),
                onButtonClick = { onSearch(searchText.value.trim(), selectedTab.value) },
            )

            is AssetsState.Data -> AssetsList(
                query = remember(searchText.value) { searchText.value.trim() },
                items = state.items,
                hasMore = state.hasMore,
                isLoadingMore = state.isLoadingMore,
                listState = listState,
                onLoadMore = onLoadMore,
                onAssetClick = onAssetClick,
            )
        }
    }
}

@Composable
private fun AssetsList(
    query: String,
    items: List<AssetItem>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    listState: LazyListState,
    onLoadMore: () -> Unit,
    onAssetClick: (AssetItem) -> Unit,
) {
    val shouldLoadMore by remember(hasMore) {
        derivedStateOf {
            if (!hasMore) {
                return@derivedStateOf false
            }
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - LOAD_MORE_THRESHOLD
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                keyboardController?.hide()
                focusManager.clearFocus()
                return Offset.Zero
            }
        }
    }

    if (items.isEmpty()) {
        MoonEmptyScreen(
            text = stringResource(Localization.not_found),
            description = stringResource(Localization.search_no_result, query)
        )
        return
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues()

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            bottom = 16.dp + navBarPadding.calculateBottomPadding()
        ),
        modifier = Modifier.nestedScroll(nestedScrollConnection),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            if (index > 0) {
                MoonItemDivider()
            }
            AssetCell(
                item = item,
                position = defaultBundleType(items.size, index),
                onClick = { onAssetClick(item) },
            )
        }

        if (isLoadingMore) {
            item {
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

private fun mockAssetItem(
    symbol: String,
    name: String,
    price: String,
    change: String,
    verification: AssetRefSummary.Verification = AssetRefSummary.Verification.whitelist,
) = AssetItem(
    id = "mock::$symbol",
    symbol = symbol,
    name = name,
    imageUrl = "",
    formattedPrice = price,
    formattedChange = change,
    verification = verification,
)

private val previewItems = listOf(
    mockAssetItem("TON", "Toncoin", "$5.42", "+ 3.21 %"),
    mockAssetItem("USDT", "Tether", "$1.00", "0.00 %"),
    mockAssetItem("BTC", "Bitcoin", "$98 430", "- 1.54 %", AssetRefSummary.Verification.trusted),
    mockAssetItem("ETH", "Ethereum", "$3 210", "+ 2.10 %"),
    mockAssetItem("NOT", "Notcoin", "$0.0071", "- 0.87 %"),
)

@Preview
@Composable
private fun AssetsScreenPreview() {
    ThemedPreview(isDarkOnly = true) {
        AssetsContent(
            state = AssetsState.Data(
                items = previewItems,
                nextCursor = null,
                isLoadingMore = false,
                query = "",
                tab = AssetsTab.all,
            ),
            focusSearch = false,
            initialTab = AssetsTab.all,
            onSearch = { _, _ -> },
            onLoadMore = {},
            onAssetClick = { _ -> },
            onBack = {},
        )
    }
}

