package com.tonapps.perps.screens.markets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.tonapps.paging.collectAsStateWorkaround
import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLoadException
import com.tonapps.perps.data.PerpsMarketFilter
import com.tonapps.perps.data.PerpsPositionSide
import com.tonapps.perps.data.PerpsSort
import com.tonapps.perps.data.USD_CURRENCY
import com.tonapps.perps.data.formatCompactPrice
import com.tonapps.perps.data.formatPrice
import com.tonapps.perps.data.formatSignedPercent
import com.tonapps.perps.data.perpsTicker
import com.tonapps.perps.data.withLivePrice
import com.tonapps.wallet.localization.Localization
import kotlinx.collections.immutable.toPersistentList
import ui.components.moon.MoonActionSelector
import ui.components.moon.MoonChipBarCell
import ui.components.moon.MoonItem
import ui.components.moon.MoonActionSelectorStyle
import ui.components.moon.MoonAsyncImage
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelDefault
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.cell.MoonRetryCell
import ui.components.moon.cell.MoonSearchCell
import ui.components.moon.cell.defaultBundleType
import ui.components.moon.container.MoonScaffold
import ui.components.moon.screen.MoonEmptyScreen
import ui.components.moon.screen.MoonEmptyScreenType
import ui.components.popup.ActionMenuHorizontalAlignment
import ui.preview.ThemedPreview
import ui.theme.Dimens
import ui.theme.UIKit
import java.math.BigDecimal

@Composable
fun PerpsMarketsScreen(
    feature: PerpsMarketsFeature,
    onOpenMarket: (marketIndex: Int, symbol: String) -> Unit,
    onBack: () -> Unit,
) {
    val pagingItems = feature.markets.collectAsLazyPagingItems()
    val filter by feature.filter.collectAsStateWorkaround()
    val debouncedQuery by feature.debouncedQuery.collectAsStateWorkaround()
    val sort by feature.sort.collectAsStateWorkaround()
    val livePrices by feature.livePrices.collectAsStateWorkaround()

    LifecycleResumeEffect(feature) {
        feature.sendAction(PerpsMarketsAction.SetActive(true))
        onPauseOrDispose {
            feature.sendAction(PerpsMarketsAction.SetActive(false))
        }
    }

    PerpsMarketsContent(
        pagingItems = pagingItems,
        filter = filter,
        debouncedQuery = debouncedQuery,
        sort = sort,
        livePrices = livePrices,
        onQueryChange = { feature.sendAction(PerpsMarketsAction.SetQuery(it)) },
        onFilterChange = { feature.sendAction(PerpsMarketsAction.SetFilter(it)) },
        onSortChange = { feature.sendAction(PerpsMarketsAction.SetSort(it)) },
        onOpenMarket = onOpenMarket,
        onRowVisible = { symbol, visible ->
            feature.sendAction(PerpsMarketsAction.RowVisible(symbol, visible))
        },
        onBack = onBack,
    )
}

@Composable
private fun PerpsMarketsContent(
    pagingItems: LazyPagingItems<PerpMarket>,
    filter: PerpsMarketFilter,
    debouncedQuery: String,
    sort: PerpsSort,
    livePrices: Map<String, BigDecimal>,
    onQueryChange: (String) -> Unit,
    onFilterChange: (PerpsMarketFilter) -> Unit,
    onSortChange: (PerpsSort) -> Unit,
    onOpenMarket: (marketIndex: Int, symbol: String) -> Unit,
    onRowVisible: (symbol: String, visible: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    MoonScaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding(),
        title = stringResource(Localization.perps_explore),
        onBack = onBack,
    ) {
        val searchText = rememberSaveable { mutableStateOf("") }
        // The feature's query node rebuilds empty after process death while searchText survives.
        LaunchedEffect(Unit) {
            if (searchText.value.isNotBlank()) {
                onQueryChange(searchText.value)
            }
        }
        MoonSearchCell(
            placeholder = stringResource(Localization.perps_search_hint),
            searchText = searchText,
            onChanged = onQueryChange,
        )

        val chips = listOf(
            MoonItem(
                id = PerpsMarketFilter.ALL.ordinal,
                title = stringResource(Localization.perps_filter_all),
            ),
            MoonItem(
                id = PerpsMarketFilter.TOKENS.ordinal,
                title = stringResource(Localization.perps_filter_tokens),
            ),
            MoonItem(
                id = PerpsMarketFilter.COMMODITIES.ordinal,
                title = stringResource(Localization.perps_filter_commodities),
            ),
            MoonItem(
                id = PerpsMarketFilter.STOCKS.ordinal,
                title = stringResource(Localization.perps_filter_stocks),
            ),
            MoonItem(
                id = PerpsMarketFilter.ETFS.ordinal,
                title = stringResource(Localization.perps_filter_etfs),
            ),
        ).toPersistentList()
        MoonChipBarCell(
            filters = chips,
            selectedId = filter.ordinal,
            onSelect = { onFilterChange(PerpsMarketFilter.entries[it.id]) },
        )

        Box(modifier = Modifier.fillMaxSize()) {
            val refresh = pagingItems.loadState.refresh
            val append = pagingItems.loadState.append
            val isLoading = refresh is LoadState.Loading ||
                (pagingItems.itemCount == 0 && append is LoadState.Loading)
            val failure = refresh as? LoadState.Error
                ?: (append as? LoadState.Error)?.takeIf { pagingItems.itemCount == 0 }
            when {
                isLoading && pagingItems.itemCount == 0 -> {
                    MoonLoaderCell(height = 240.dp)
                }

                failure != null -> PerpsMarketsError(
                    error = (failure.error as? PerpsLoadException)?.error ?: PerpsError.Unknown,
                    onRetry = pagingItems::retry,
                )

                !isLoading && pagingItems.itemCount == 0 -> {
                    PerpsMarketsEmpty(query = searchText.value)
                }

                else -> PerpsMarketsList(
                    pagingItems = pagingItems,
                    filter = filter,
                    sort = sort,
                    query = debouncedQuery,
                    livePrices = livePrices,
                    onOpenMarket = onOpenMarket,
                    onRowVisible = onRowVisible,
                )
            }

            if (failure == null) {
                PerpsFloatingSortSelector(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    sort = sort,
                    onSortChange = onSortChange,
                )
            }
        }
    }
}

@Composable
private fun PerpsMarketsError(
    error: PerpsError,
    onRetry: () -> Unit,
) {
    MoonEmptyScreen(
        text = stringResource(Localization.perps_markets_load_failed),
        description = perpsErrorDescription(error),
        type = MoonEmptyScreenType.Error,
        buttonText = stringResource(Localization.perps_retry),
        onButtonClick = onRetry,
    )
}

@Composable
private fun PerpsMarketsEmpty(query: String) {
    MoonEmptyScreen(
        text = stringResource(Localization.cant_find_anything),
        description = if (query.isBlank()) {
            stringResource(Localization.no_results)
        } else {
            stringResource(Localization.search_no_result, query)
        },
        type = MoonEmptyScreenType.Empty,
    )
}

@Composable
private fun PerpsMarketsList(
    pagingItems: LazyPagingItems<PerpMarket>,
    filter: PerpsMarketFilter,
    sort: PerpsSort,
    query: String,
    livePrices: Map<String, BigDecimal>,
    onOpenMarket: (marketIndex: Int, symbol: String) -> Unit,
    onRowVisible: (symbol: String, visible: Boolean) -> Unit,
) {
    val listState = rememberLazyListState()
    var scrollToTopPending by remember { mutableStateOf(false) }
    var filtersSeen by remember { mutableStateOf(false) }
    LaunchedEffect(sort, query, filter) {
        if (filtersSeen) {
            scrollToTopPending = true
        } else {
            filtersSeen = true
        }
    }
    val refresh = pagingItems.loadState.refresh
    LaunchedEffect(refresh, scrollToTopPending) {
        if (scrollToTopPending && refresh is LoadState.NotLoading) {
            listState.scrollToItem(0)
            scrollToTopPending = false
        }
    }
    val navBottom = WindowInsets.navigationBars.asPaddingValues()
        .calculateBottomPadding()
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = navBottom + Dimens.heightBar),
    ) {
        items(
            count = pagingItems.itemCount,
            key = pagingItems.itemKey { it.marketIndex },
        ) { index ->
            val market = pagingItems[index] ?: return@items
            PerpMarketRow(
                market = market.withLivePrice(livePrices[perpsTicker(market.symbol)]),
                position = defaultBundleType(pagingItems.itemCount, index),
                onClick = { onOpenMarket(market.marketIndex, market.symbol) },
                onVisibilityChange = { onRowVisible(market.symbol, it) },
            )
        }

        if (pagingItems.loadState.append is LoadState.Loading) {
            item(key = "append_loading") {
                MoonLoaderCell(height = 64.dp)
            }
        }

        if (pagingItems.loadState.append is LoadState.Error) {
            item(key = "append_error") {
                MoonRetryCell(
                    message = stringResource(Localization.perps_markets_load_failed),
                    buttonText = stringResource(Localization.perps_retry),
                    onRetry = pagingItems::retry,
                )
            }
        }
    }
}

@Composable
private fun PerpsFloatingSortSelector(
    sort: PerpsSort,
    onSortChange: (PerpsSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.heightBar + navBottom)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        UIKit.colorScheme.background.page,
                    ),
                ),
            )
            .padding(bottom = navBottom),
        contentAlignment = Alignment.Center,
    ) {
        PerpsSortSelector(
            sort = sort,
            onSortChange = onSortChange,
            style = MoonActionSelectorStyle.Tertiary,
            menuAlignment = ActionMenuHorizontalAlignment.Center,
        )
    }
}

@Composable
fun PerpsSortSelector(
    sort: PerpsSort,
    onSortChange: (PerpsSort) -> Unit,
    modifier: Modifier = Modifier,
    style: MoonActionSelectorStyle = MoonActionSelectorStyle.Secondary,
    menuAlignment: ActionMenuHorizontalAlignment = ActionMenuHorizontalAlignment.Start,
) {
    val labels = listOf(
        stringResource(Localization.perps_volume),
        stringResource(Localization.perps_price_change),
        stringResource(Localization.perps_open_interest),
    )
    MoonActionSelector(
        modifier = modifier,
        items = labels,
        selectedIndex = sort.ordinal,
        onSelect = { onSortChange(PerpsSort.entries[it]) },
        style = style,
        menuAlignment = menuAlignment,
        menuOffset = DpOffset(0.dp, 4.dp),
    )
}

@Composable
fun PerpMarketRow(
    market: PerpMarket,
    onClick: () -> Unit,
    onVisibilityChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    position: MoonBundlePosition = MoonBundlePosition.Default,
    currencyCode: String = USD_CURRENCY,
) {
    DisposableEffect(market.marketIndex) {
        onVisibilityChange(true)
        onDispose { onVisibilityChange(false) }
    }
    MoonBundleCell(
        modifier = modifier,
        onClick = onClick,
        position = position,
    ) {
        PerpMarketRowContent(market = market, currencyCode = currencyCode)
    }
}

@Composable
private fun PerpMarketRowContent(market: PerpMarket, currencyCode: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PerpsAssetIcon(symbol = market.symbol, iconUrl = market.iconUrl)
        Spacer(modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = market.symbol,
                    style = UIKit.typography.label1,
                    color = UIKit.colorScheme.text.primary,
                    maxLines = 1,
                )
                if (market.maxLeverage > 0) {
                    MoonLabel(
                        text = stringResource(
                            Localization.perps_leverage_pattern,
                            market.maxLeverage,
                        ),
                    )
                }
            }
            val volume = market.volume24h?.takeIf { it.signum() > 0 }
            if (volume != null) {
                Text(
                    text = stringResource(
                        Localization.volume_short_pattern,
                        formatCompactPrice(volume, currencyCode),
                    ),
                    style = UIKit.typography.body3,
                    color = UIKit.colorScheme.text.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                market.name?.let { name ->
                    Text(
                        text = name,
                        style = UIKit.typography.body3,
                        color = UIKit.colorScheme.text.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = market.price?.let { formatPrice(it, currencyCode) } ?: PERPS_EMPTY_VALUE,
                style = UIKit.typography.label1,
                color = UIKit.colorScheme.text.primary,
                maxLines = 1,
            )
            Text(
                text = market.priceChange24hPercent
                    ?.let { formatSignedPercent(it) }
                    ?: PERPS_EMPTY_VALUE,
                style = UIKit.typography.body3,
                color = perpsChangeColor(market.priceChange24hPercent),
                maxLines = 1,
            )
        }
    }
}

@Composable
fun PerpsMonogram(
    symbol: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color = UIKit.colorScheme.background.contentTint, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = symbol.take(1).uppercase(),
            style = UIKit.typography.label2,
            color = UIKit.colorScheme.text.secondary,
        )
    }
}

@Composable
fun PerpsAssetIcon(
    symbol: String,
    iconUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    if (iconUrl != null) {
        var loaded by remember(iconUrl) { mutableStateOf(false) }
        Box(modifier = modifier.size(size)) {
            if (loaded) {
                Box(
                    modifier = Modifier
                        .size(size)
                        .background(
                            color = UIKit.colorScheme.background.contentTint,
                            shape = CircleShape,
                        ),
                )
            } else {
                PerpsMonogram(symbol = symbol, size = size)
            }
            MoonAsyncImage(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape),
                image = iconUrl,
                size = size,
                onSuccess = { loaded = true },
            )
        }
    } else {
        PerpsMonogram(symbol = symbol, modifier = modifier, size = size)
    }
}

@Composable
internal fun PerpsSideLabel(side: PerpsPositionSide) {
    if (side == PerpsPositionSide.LONG) {
        MoonLabel(
            text = stringResource(Localization.perps_long),
            colors = MoonLabelDefault.success(),
        )
    } else {
        MoonLabel(
            text = stringResource(Localization.perps_short),
            colors = MoonLabelDefault.error(),
        )
    }
}

@Composable
fun perpsChangeColor(value: BigDecimal?): Color {
    return when (value?.signum()) {
        1 -> UIKit.colorScheme.accent.green
        -1 -> UIKit.colorScheme.accent.red
        else -> UIKit.colorScheme.text.secondary
    }
}

@Composable
fun perpsErrorDescription(error: PerpsError?): String? {
    if (error == PerpsError.ServiceUnavailable) {
        return stringResource(Localization.perps_service_unavailable)
    }
    return null
}

private val previewMarkets = listOf(
    PerpMarket(
        marketIndex = 1,
        symbol = "BTC",
        name = "Bitcoin",
        iconUrl = null,
        maxLeverage = 40,
        price = BigDecimal("66141.70"),
        priceChange24hPercent = BigDecimal("4.37"),
        volume24h = BigDecimal("2360000000"),
        openInterestUsd = BigDecimal("1700000000"),
        fundingRateHourly = BigDecimal("0.0000032385"),
        priceDecimals = 2,
        sizeDecimals = 6,
    ),
    PerpMarket(
        marketIndex = 2,
        symbol = "ETH",
        name = "Ethereum",
        iconUrl = null,
        maxLeverage = 25,
        price = BigDecimal("3512.88"),
        priceChange24hPercent = BigDecimal("-1.07"),
        volume24h = BigDecimal("311900000"),
        openInterestUsd = BigDecimal("420000000"),
        fundingRateHourly = BigDecimal("-0.0000125"),
        priceDecimals = 2,
        sizeDecimals = 4,
    ),
)

@Preview
@Composable
private fun PerpMarketRowPreview() {
    ThemedPreview(isDarkOnly = true) {
        Column {
            PerpMarketRow(
                market = previewMarkets[0].copy(volume24h = null),
                position = MoonBundlePosition.Header,
                onClick = {},
                onVisibilityChange = {},
            )
            PerpMarketRow(
                market = previewMarkets[1],
                position = MoonBundlePosition.Footer,
                onClick = {},
                onVisibilityChange = {},
            )
        }
    }
}

@Preview
@Composable
private fun PerpsMarketsEmptyPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsMarketsEmpty(query = "PONS")
    }
}

@Preview
@Composable
private fun PerpsMarketsErrorPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsMarketsError(
            error = PerpsError.ServiceUnavailable,
            onRetry = {},
        )
    }
}
