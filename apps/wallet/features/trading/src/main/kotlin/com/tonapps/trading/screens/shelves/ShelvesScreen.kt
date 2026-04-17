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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.tonapps.mvi.props.observeSafeState
import com.tonapps.wallet.localization.Localization
import io.tradingapi.models.AssetRefSummary
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
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.cell.MoonSearchCell
import ui.components.moon.container.MoonScaffold
import ui.components.moon.screen.MoonEmptyScreen
import ui.preview.ThemedPreview
import ui.theme.Dimens
import ui.theme.Shapes
import ui.theme.UIKit
import java.time.OffsetDateTime
import kotlin.collections.chunked
import kotlin.collections.forEach
import kotlin.collections.orEmpty

@Composable
fun ShelvesScreen(
    feature: ShelvesFeature,
    onOpenAssets: () -> Unit,
    onOpenAssetDetails: (assetId: String) -> Unit,
) {
    val state by feature.state.global.observeSafeState()

    ShelvesContent(
        state = state,
        onRetry = { feature.sendAction(ShelvesAction.Retry) },
        onOpenAssets = onOpenAssets,
        onOpenAssetDetails = onOpenAssetDetails,
    )
}

@Composable
private fun ShelvesContent(
    state: ShelvesState,
    onRetry: () -> Unit,
    onOpenAssets: () -> Unit,
    onOpenAssetDetails: (assetId: String) -> Unit,
) {
    MoonScaffold(
        topBar = {
            MoonSearchCell(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(bottom = 8.dp),
                placeholder = stringResource(Localization.search_by_ticker),
                onClick = { }
            )
        }
    ) {
        when (state) {
            ShelvesState.Loading -> MoonLoaderCell()

            ShelvesState.Empty -> MoonEmptyScreen(
                text = stringResource(Localization.cant_find_anything),
                buttonText = stringResource(Localization.retry),
                onButtonClick = onRetry,
            )

            is ShelvesState.Data -> {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(bottom = Dimens.heightBar)
                ) {
                    state.shelfGroups.forEach { group ->
                        ShelfGroupItem(
                            group = group,
                            onClickSeeAll = onOpenAssets,
                            onAssetClick = { onOpenAssetDetails(it.asset.id) },
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
    onClickSeeAll: () -> Unit,
    onAssetClick: (MarketItem) -> Unit,
) {
    val tabs = group.items.map { MoonTabItem(id = it.key.ordinal, title = it.title) }.toImmutableList()
    val seeAllEnabled = group.items.any { it.seeAll.enabled }

    var selectedId by remember(group.items) {
        mutableIntStateOf(tabs.firstOrNull()?.id ?: 0)
    }

    val items = group.items
        .firstOrNull { it.key.ordinal == selectedId }
        ?.items
        .orEmpty()

    val showTabs = group.items.size > 1

    ShelfContainer(
        title = group.name,
        seeAllEnabled = seeAllEnabled,
        onClickSeeAll = onClickSeeAll,
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
            text = item.asset.symbol,
            style = UIKit.typography.body3,
            color = UIKit.colorScheme.text.primary,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = item.metrics.change24hPercent.formatChangePercent(),
            style = UIKit.typography.body3,
            color = when {
                item.metrics.change24hPercent.startsWith("-") -> UIKit.colorScheme.accent.red
                item.metrics.change24hPercent == "0.00" -> UIKit.colorScheme.text.secondary
                else -> UIKit.colorScheme.accent.green
            },
            maxLines = 1,
        )
    }
}

private fun String.formatChangePercent(): String {
    val value = when {
        startsWith("-") -> replace("-", "- ")
        equals("0.00") -> this
        else -> "+ $this"
    }
    return "$value %"
}

private fun mockItem(symbol: String, change: String) =
    MarketItem(
        asset = AssetRefSummary(
            chain = "ton",
            id = "ton::default::$symbol",
            address = "0:000",
            symbol = symbol,
            name = symbol,
            decimals = 9,
            imageUrl = "",
        ),
        metrics = MarketMetricsSummary(
            price = "0.00001",
            change24hPercent = change,
            provider = "mock",
            asOf = OffsetDateTime.now(),
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
                seeAll = ShelfConfigSeeAll(enabled = true, route = "assets"),
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
                seeAll = ShelfConfigSeeAll(enabled = true, route = "assets"),
                items = listOf(
                    mockItem("B3", "22.10"),
                    mockItem("VES", "12.50"),
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
                seeAll = ShelfConfigSeeAll(enabled = true, route = "assets"),
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
            onOpenAssetDetails = { },
        )
    }
}
