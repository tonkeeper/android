package com.tonapps.trading.screens.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.icu.Formatter
import com.tonapps.mvi.props.observeSafeState
import com.tonapps.wallet.localization.Localization
import io.tradingapi.models.AssetRefSummary
import io.tradingapi.models.AssetType
import io.tradingapi.models.AssetsTab
import io.tradingapi.models.MarketItem
import io.tradingapi.models.MarketListKey
import io.tradingapi.models.MarketMetricsSummary
import io.tradingapi.models.ShelfConfig
import io.tradingapi.models.ShelfConfigSeeAll
import io.tradingapi.models.ShelfGroup
import io.tradingapi.models.ShelfType
import kotlinx.collections.immutable.toImmutableList
import ui.components.moon.MoonAsyncImage
import ui.components.moon.MoonContentTabs
import ui.components.moon.MoonTabItem
import ui.components.moon.cell.MoonSearchCell
import ui.components.moon.container.MoonScaffold
import ui.components.moon.screen.MoonEmptyScreen
import ui.preview.ThemedPreview
import ui.theme.Dimens
import ui.theme.Shapes
import ui.theme.UIKit

@Composable
fun ShelvesScreen(
    feature: ShelvesFeature,
    onOpenAssets: (AssetsTab) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAssetDetails: (MarketItem) -> Unit,
    scrollToShelfKey: MarketListKey? = null,
    onScrollToShelfHandled: () -> Unit = {},
) {
    val state by feature.state.global.observeSafeState()

    ShelvesContent(
        state = state,
        onRetry = { feature.sendAction(ShelvesAction.Retry) },
        onOpenAssets = onOpenAssets,
        onOpenSearch = onOpenSearch,
        onOpenAssetDetails = {
            feature.trackAssetClick(it)
            onOpenAssetDetails(it)
        },
        scrollToShelfKey = scrollToShelfKey,
        onScrollToShelfHandled = onScrollToShelfHandled,
    )
}

@Composable
private fun ShelvesContent(
    state: ShelvesState,
    onRetry: () -> Unit,
    onOpenAssets: (AssetsTab) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenAssetDetails: (MarketItem) -> Unit,
    scrollToShelfKey: MarketListKey? = null,
    onScrollToShelfHandled: () -> Unit = {},
) {
    MoonScaffold(
        topBar = {
            MoonSearchCell(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(vertical = 16.dp),
                placeholder = stringResource(Localization.search_by_ticker),
                onClick = onOpenSearch
            )
        }
    ) {
        when (state) {
            ShelvesState.Loading -> ShelvesShimmer()

            ShelvesState.Empty -> MoonEmptyScreen(
                text = stringResource(Localization.cant_find_anything),
                buttonText = stringResource(Localization.retry),
                onButtonClick = onRetry,
            )

            is ShelvesState.Data -> {
                val listState = rememberLazyListState()

                LaunchedEffect(scrollToShelfKey, state.shelfGroups) {
                    val key = scrollToShelfKey ?: return@LaunchedEffect
                    val targetIndex = state.shelfGroups.indexOfFirst { group ->
                        group.items.any { it.key == key }
                    }
                    if (targetIndex >= 0) {
                        listState.animateScrollToItem(targetIndex)
                    }
                    onScrollToShelfHandled()
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(bottom = Dimens.heightBar),
                ) {
                    items(
                        items = state.shelfGroups,
                        key = { group -> group.name },
                    ) { group ->
                        ShelfGroupItem(
                            group = group,
                            initiallySelectedKey = scrollToShelfKey?.takeIf {
                                group.items.any { item -> item.key == it }
                            },
                            onClickSeeAll = onOpenAssets,
                            onAssetClick = { onOpenAssetDetails(it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfGroupItem(
    group: ShelfGroup,
    onClickSeeAll: (AssetsTab) -> Unit,
    onAssetClick: (MarketItem) -> Unit,
    initiallySelectedKey: MarketListKey? = null,
) {
    val tabs = group.items.map { MoonTabItem(id = it.key.ordinal, title = it.title) }.toImmutableList()
    val seeAllRoute = group.items.firstOrNull { it.seeAll.enabled }?.seeAll?.route

    var selectedId by remember(group.items) {
        val initialId = initiallySelectedKey?.ordinal
            ?.takeIf { id -> tabs.any { it.id == id } }
            ?: tabs.firstOrNull()?.id
            ?: 0
        mutableIntStateOf(initialId)
    }

    val items = group.items
        .firstOrNull { it.key.ordinal == selectedId }
        ?.items
        .orEmpty()

    val showTabs = group.items.size > 1

    ShelfContainer(
        title = group.name,
        seeAllEnabled = seeAllRoute != null,
        onClickSeeAll = { seeAllRoute?.let { onClickSeeAll(it) } },
    ) {
        if (showTabs) {
            MoonContentTabs(
                items = tabs,
                selectedId = selectedId,
                onSelect = { selectedId = it.id },
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        AssetsGrid(items = items, onAssetClick)
    }
}

@Composable
private fun ShelfContainer(
    title: String,
    seeAllEnabled: Boolean,
    onClickSeeAll: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.padding(vertical = 12.dp),
                text = title,
                style = UIKit.typography.label1,
                color = UIKit.colorScheme.text.primary,
                maxLines = 1,
            )
            if (seeAllEnabled) {
                Text(
                    modifier = Modifier
                        .clip(Shapes.medium12)
                        .clickable(onClick = onClickSeeAll)
                        .padding(vertical = 12.dp),
                    text = stringResource(Localization.show_all),
                    style = UIKit.typography.label1,
                    color = UIKit.colorScheme.text.accent,
                )
            }
        }
        Column(
            modifier = Modifier
                .background(shape = Shapes.medium, color = UIKit.colorScheme.background.content)
                .padding(8.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun AssetsGrid(
    items: List<MarketItem>,
    onAssetClick: (MarketItem) -> Unit,
) {
    items.chunked(4).forEach { rowItems ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        ) {
            rowItems.forEach { item ->
                Box(modifier = Modifier.weight(1f)) {
                    AssetItem(
                        item = item,
                        onClick = { onAssetClick(item) },
                    )
                }
            }
            repeat(4 - rowItems.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AssetItem(
    item: MarketItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Shapes.medium12)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MoonAsyncImage(
            modifier = Modifier
                .background(color = UIKit.colorScheme.background.contentTint, shape = CircleShape)
                .size(56.dp)
                .clip(CircleShape),
            image = item.asset.imageUrl,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = CurrencyFormatter.displaySymbol(item.asset.symbol),
            style = UIKit.typography.body3,
            color = UIKit.colorScheme.text.primary,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = Formatter.percent(item.metrics.change24hPercent),
            style = UIKit.typography.body3,
            color = when {
                item.metrics.change24hPercent.startsWith("-") -> UIKit.colorScheme.accent.red
                item.metrics.change24hPercent.startsWith("+") -> UIKit.colorScheme.accent.green
                else -> UIKit.colorScheme.text.secondary
            },
            maxLines = 1,
        )
    }
}

private fun mockItem(symbol: String, change: String) =
    MarketItem(
        asset = AssetRefSummary(
            assetType = AssetType.asset,
            id = "ton::default::$symbol",
            symbol = symbol,
            name = symbol,
            decimals = 9,
            imageUrl = "",
            trustScore = 0,
            isScam = false,
            verification = AssetRefSummary.Verification.whitelist,
        ),
        metrics = MarketMetricsSummary(
            price = "0.00001",
            change24hPercent = change,
            provider = "mock",
            asOf = "",
        ),
    )

private val previewShelfGroups = listOf(
    ShelfGroup(
        name = "Top Movers",
        items = listOf(
            ShelfConfig(
                key = MarketListKey.top_gainers,
                title = "Top Gainers",
                type = ShelfType.grid,
                source = "mock",
                seeAll = ShelfConfigSeeAll(enabled = true, route = AssetsTab.all),
                items = listOf(
                    mockItem("SHREK", "18.43"),
                    mockItem("WZP", "-1.40"),
                ),
            ),
            ShelfConfig(
                key = MarketListKey.top_losers,
                title = "Top Losers",
                type = ShelfType.grid,
                source = "mock",
                seeAll = ShelfConfigSeeAll(enabled = true, route = AssetsTab.all),
                items = listOf(
                    mockItem("B3", "+22.10"),
                    mockItem("VES", "+12.50"),
                ),
            ),
        ),
    ),
    ShelfGroup(
        name = "Most Traded",
        items = listOf(
            ShelfConfig(
                key = MarketListKey.most_traded,
                title = "Most Traded",
                type = ShelfType.grid,
                source = "mock",
                seeAll = ShelfConfigSeeAll(enabled = true, route = AssetsTab.all),
                items = listOf(
                    mockItem("TRUMPUS", "-4.50"),
                    mockItem("NOOTYA", "-2.30"),
                ),
            ),
        ),
    ),
)

@Preview
@Composable
private fun ShelvesScreenPreview() {
    ThemedPreview(isDarkOnly = true) {
        ShelvesContent(
            state = ShelvesState.Data(shelfGroups = previewShelfGroups),
            onRetry = { },
            onOpenAssets = { },
            onOpenSearch = { },
            onOpenAssetDetails = { },
        )
    }
}

@Preview
@Composable
private fun ShelvesScreenLoadingPreview() {
    ThemedPreview(isDarkOnly = true) {
        ShelvesContent(
            state = ShelvesState.Loading,
            onRetry = { },
            onOpenAssets = { },
            onOpenSearch = { },
            onOpenAssetDetails = { },
        )
    }
}
