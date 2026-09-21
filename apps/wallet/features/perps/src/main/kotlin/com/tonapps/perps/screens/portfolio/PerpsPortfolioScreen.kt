package com.tonapps.perps.screens.portfolio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.tonapps.paging.collectAsStateWorkaround
import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.data.PerpsBalance
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLoadException
import com.tonapps.perps.data.PerpsPortfolio
import com.tonapps.perps.data.PerpsPosition
import com.tonapps.perps.data.PerpsPositionSide
import com.tonapps.perps.data.PerpsSort
import com.tonapps.perps.data.formatSignedPercent
import com.tonapps.perps.data.formatSignedUsd
import com.tonapps.perps.data.formatUsd
import com.tonapps.perps.data.perpsTicker
import com.tonapps.perps.data.unrealizedPnlPercent
import com.tonapps.perps.data.withLivePrice
import com.tonapps.perps.screens.markets.PerpMarketRow
import com.tonapps.perps.screens.markets.PerpsAssetIcon
import com.tonapps.perps.screens.markets.PerpsSideLabel
import com.tonapps.perps.screens.markets.PerpsSortSelector
import com.tonapps.perps.screens.markets.perpsChangeColor
import com.tonapps.perps.screens.markets.perpsErrorDescription
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import ui.components.moon.ButtonColorsPrimary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonBottomBar
import ui.components.moon.MoonCircleIcon
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonLabel
import ui.components.moon.MoonTopAppBar
import ui.components.moon.MoonTopAppBarIcon
import ui.components.moon.MoonTopAppBarSubtitle
import ui.components.moon.MoonTopAppBarTitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundleCellContent
import ui.components.moon.cell.MoonBundleTitleCell
import ui.components.moon.cell.defaultBundleType
import ui.components.moon.cell.MoonCardCell
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.cell.MoonRetryCell
import ui.components.moon.container.MoonScaffold
import ui.components.moon.moonBottomBarHeight
import ui.moon.MoonToastHost
import ui.moon.rememberMoonToastHostState
import ui.preview.ThemedPreview
import ui.theme.Shapes
import ui.theme.UIKit
import java.math.BigDecimal

@Composable
fun PerpsPortfolioScreen(
    feature: PerpsPortfolioFeature,
    onOpenMarket: (marketIndex: Int, symbol: String) -> Unit,
    onOpenExplore: () -> Unit,
    onBack: () -> Unit,
) {
    val portfolio by feature.portfolio.collectAsStateWorkaround()
    val portfolioLoading by feature.portfolioLoading.collectAsStateWorkaround()
    val portfolioError by feature.portfolioError.collectAsStateWorkaround()
    val sort by feature.sort.collectAsStateWorkaround()
    val exploreMarkets = feature.exploreMarkets.collectAsLazyPagingItems()
    val livePrices by feature.livePrices.collectAsStateWorkaround()

    val toastHost = rememberMoonToastHostState()
    val scope = rememberCoroutineScope()
    val comingSoonText = stringResource(Localization.perps_coming_soon)

    LifecycleResumeEffect(feature) {
        feature.sendAction(PerpsPortfolioAction.SetActive(true))
        onPauseOrDispose {
            feature.sendAction(PerpsPortfolioAction.SetActive(false))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PerpsPortfolioContent(
            portfolio = portfolio,
            portfolioLoading = portfolioLoading,
            portfolioError = portfolioError,
            sort = sort,
            exploreMarkets = exploreMarkets,
            livePrices = livePrices,
            onSortChange = { feature.sendAction(PerpsPortfolioAction.SetSort(it)) },
            onRetry = { feature.sendAction(PerpsPortfolioAction.Retry) },
            onOpenMarket = onOpenMarket,
            onRowVisible = { symbol, visible ->
                feature.sendAction(PerpsPortfolioAction.RowVisible(symbol, visible))
            },
            onOpenExplore = onOpenExplore,
            onComingSoon = {
                if (toastHost.currentData == null) {
                    scope.launch { toastHost.showToast(comingSoonText) }
                }
            },
            onBack = onBack,
        )
        MoonToastHost(toastHost)
    }
}

@Composable
private fun PerpsPortfolioContent(
    portfolio: PerpsPortfolio?,
    portfolioLoading: Boolean,
    portfolioError: PerpsError?,
    sort: PerpsSort,
    exploreMarkets: LazyPagingItems<PerpMarket>,
    livePrices: Map<String, BigDecimal>,
    onSortChange: (PerpsSort) -> Unit,
    onRetry: () -> Unit,
    onOpenMarket: (marketIndex: Int, symbol: String) -> Unit,
    onRowVisible: (symbol: String, visible: Boolean) -> Unit,
    onOpenExplore: () -> Unit,
    onComingSoon: () -> Unit,
    onBack: () -> Unit,
) {
    MoonScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            MoonTopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = { MoonTopAppBarTitle(text = stringResource(Localization.perps_title)) },
                onTitleClick = onComingSoon,
                subtitle = {
                    MoonTopAppBarSubtitle(
                        text = stringResource(Localization.perps_learn_basics),
                        color = UIKit.colorScheme.text.accent,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    MoonItemIcon(
                        painter = painterResource(UIKitIcon.ic_information_circle_16),
                        color = UIKit.colorScheme.text.accent,
                    )
                },
                navigationIconRes = UIKitIcon.ic_chevron_left_16,
                onNavigationClick = onBack,
                actions = {
                    MoonTopAppBarIcon(
                        painter = painterResource(UIKitIcon.ic_clock_outline_28),
                        onClick = onComingSoon,
                    )
                    MoonTopAppBarIcon(
                        painter = painterResource(UIKitIcon.ic_magnifying_glass_16),
                        onClick = onOpenExplore,
                    )
                },
                backgroundColor = Color.Transparent,
            )
        },
    ) {
        val positions = portfolio?.positions.orEmpty()
        val availableBalance = portfolio?.balance?.availableBalance
        val isZeroState = !portfolioLoading && portfolioError == null && positions.isEmpty() &&
            (availableBalance == null || availableBalance.signum() == 0)

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = if (isZeroState) {
                    PaddingValues(bottom = moonBottomBarHeight())
                } else {
                    WindowInsets.navigationBars.asPaddingValues()
                },
            ) {
                item(key = "balance") {
                    when {
                        portfolioLoading -> MoonLoaderCell(height = 96.dp)

                        portfolioError != null -> MoonRetryCell(
                            message = stringResource(Localization.something_went_wrong),
                            buttonText = stringResource(Localization.perps_retry),
                            onRetry = onRetry,
                        )

                        else -> PerpsBalanceCard(
                            balance = portfolio?.balance,
                            onDeposit = onComingSoon,
                        )
                    }
                }

                if (positions.isNotEmpty()) {
                    item(key = "positions_title") {
                        MoonBundleTitleCell(title = stringResource(Localization.perps_open_positions))
                    }
                    item(key = "positions_total") {
                        PerpsPositionsTotalCard(balance = portfolio?.balance)
                    }
                    items(
                        items = positions,
                        key = { "position_${it.marketIndex}_${it.side}" },
                    ) { position ->
                        PerpsPositionRow(
                            position = position,
                            onClick = { onOpenMarket(position.marketIndex, position.symbol) },
                        )
                    }
                }

                item(key = "explore_title") {
                    MoonBundleTitleCell(
                        title = stringResource(Localization.perps_explore),
                        onClick = onOpenExplore,
                        content = {
                            PerpsSortSelector(
                                sort = sort,
                                onSortChange = onSortChange,
                            )
                        },
                    )
                }

                val exploreRefresh = exploreMarkets.loadState.refresh
                val exploreAppend = exploreMarkets.loadState.append
                val exploreFailure = exploreRefresh as? LoadState.Error ?: exploreAppend as? LoadState.Error
                when {
                    (exploreRefresh is LoadState.Loading || exploreAppend is LoadState.Loading) &&
                        exploreMarkets.itemCount == 0 ->
                        item(key = "explore_loading") {
                            MoonLoaderCell(height = 240.dp)
                        }

                    exploreFailure != null && exploreMarkets.itemCount == 0 ->
                        item(key = "explore_error") {
                            val error = (exploreFailure.error as? PerpsLoadException)?.error
                            MoonRetryCell(
                                message = perpsErrorDescription(error)
                                    ?: stringResource(Localization.perps_markets_load_failed),
                                buttonText = stringResource(Localization.perps_retry),
                                onRetry = exploreMarkets::retry,
                            )
                        }

                    else -> {
                        items(
                            count = exploreMarkets.itemCount,
                            key = exploreMarkets.itemKey { "market_${it.marketIndex}" },
                        ) { index ->
                            val market = exploreMarkets[index] ?: return@items
                            PerpMarketRow(
                                market = market.withLivePrice(livePrices[perpsTicker(market.symbol)]),
                                position = defaultBundleType(exploreMarkets.itemCount, index),
                                onClick = { onOpenMarket(market.marketIndex, market.symbol) },
                                onVisibilityChange = { onRowVisible(market.symbol, it) },
                            )
                        }

                        if (exploreMarkets.loadState.append is LoadState.Loading) {
                            item(key = "append_loading") {
                                MoonLoaderCell(height = 64.dp)
                            }
                        }

                        if (exploreMarkets.loadState.append is LoadState.Error) {
                            item(key = "append_error") {
                                MoonRetryCell(
                                    message = stringResource(Localization.perps_markets_load_failed),
                                    buttonText = stringResource(Localization.perps_retry),
                                    onRetry = exploreMarkets::retry,
                                )
                            }
                        }
                    }
                }
            }

            if (isZeroState) {
                MoonBottomBar(modifier = Modifier.align(Alignment.BottomCenter)) {
                    MoonAccentButton(
                        modifier = Modifier.weight(1f),
                        text = stringResource(Localization.perps_deposit),
                        size = ButtonSizeLarge,
                        buttonColors = ButtonColorsPrimary,
                        onClick = onComingSoon,
                    )
                }
            }
        }
    }
}

@Composable
private fun PerpsBalanceCard(
    balance: PerpsBalance?,
    onDeposit: () -> Unit,
) {
    MoonCardCell(
        image = {
            MoonCircleIcon(
                painter = painterResource(UIKitIcon.ic_perps_28),
                color = UIKit.colorScheme.accent.blue.copy(alpha = 0.12f),
                size = 44.dp,
            )
        },
        title = formatUsd(balance?.availableBalance ?: BigDecimal.ZERO),
        subtitle = stringResource(Localization.perps_balance),
        content = {
            MoonAccentButton(text = stringResource(Localization.perps_deposit), onClick = onDeposit)
        },
    )
}

@Composable
private fun PerpsPositionsTotalCard(balance: PerpsBalance?) {
    val pnl = balance?.unrealizedPnlTotal
    val pnlPercent = balance?.unrealizedPnlPercent()

    MoonBundleCell(contentPadding = MoonBundleCellContent.Default) {
        Column(modifier = Modifier.fillMaxWidth()) {
            MoonItemSubtitle(stringResource(Localization.perps_total_amount))
            Text(
                text = balance?.equity?.let { formatUsd(it) } ?: PERPS_EMPTY_VALUE,
                style = UIKit.typography.h2,
                color = UIKit.colorScheme.text.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = pnl?.let { formatSignedUsd(it) } ?: PERPS_EMPTY_VALUE,
                    style = UIKit.typography.body2,
                    color = perpsChangeColor(pnl),
                )
                Text(
                    text = pnlPercent?.let { formatSignedPercent(it) } ?: PERPS_EMPTY_VALUE,
                    style = UIKit.typography.body2,
                    color = perpsChangeColor(pnlPercent).copy(alpha = 0.48f),
                )
            }
        }
    }
}

@Composable
private fun PerpsPositionRow(
    position: PerpsPosition,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(Shapes.medium12)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PerpsAssetIcon(symbol = position.symbol, iconUrl = position.iconUrl)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = position.symbol,
                style = UIKit.typography.label1,
                color = UIKit.colorScheme.text.primary,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                position.leverage?.let { leverage ->
                    MoonLabel(text = stringResource(Localization.perps_leverage_pattern, leverage))
                }
                PerpsSideLabel(side = position.side)
            }
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = position.positionValue?.let { formatUsd(it) } ?: PERPS_EMPTY_VALUE,
                style = UIKit.typography.label1,
                color = UIKit.colorScheme.text.primary,
                maxLines = 1,
            )
            Text(
                text = position.unrealizedPnl?.let { formatSignedUsd(it) } ?: PERPS_EMPTY_VALUE,
                style = UIKit.typography.body3,
                color = perpsChangeColor(position.unrealizedPnl),
                maxLines = 1,
            )
        }
    }
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

private val previewPositions = listOf(
    PerpsPosition(
        marketIndex = 1,
        symbol = "BTC",
        iconUrl = null,
        side = PerpsPositionSide.LONG,
        positionValue = BigDecimal("540.00"),
        unrealizedPnl = BigDecimal("20.50"),
        leverage = 27,
    ),
    PerpsPosition(
        marketIndex = 2,
        symbol = "ETH",
        iconUrl = null,
        side = PerpsPositionSide.SHORT,
        positionValue = BigDecimal("120.00"),
        unrealizedPnl = BigDecimal("-4.20"),
        leverage = 10,
    ),
)

@Preview
@Composable
private fun PerpsPortfolioZeroPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsPortfolioContent(
            portfolio = PerpsPortfolio(balance = null, positions = emptyList()),
            portfolioLoading = false,
            portfolioError = null,
            sort = PerpsSort.VOLUME,
            exploreMarkets = flowOf(PagingData.from(previewMarkets)).collectAsLazyPagingItems(),
            livePrices = emptyMap(),
            onSortChange = {},
            onRetry = {},
            onOpenMarket = { _, _ -> },
            onRowVisible = { _, _ -> },
            onOpenExplore = {},
            onComingSoon = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun PerpsPortfolioFundedPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsPortfolioContent(
            portfolio = PerpsPortfolio(
                balance = PerpsBalance(
                    availableBalance = BigDecimal("980.15"),
                    equity = BigDecimal("1865.89"),
                    unrealizedPnlTotal = BigDecimal("103.80"),
                ),
                positions = emptyList(),
            ),
            portfolioLoading = false,
            portfolioError = null,
            sort = PerpsSort.VOLUME,
            exploreMarkets = flowOf(PagingData.from(previewMarkets)).collectAsLazyPagingItems(),
            livePrices = emptyMap(),
            onSortChange = {},
            onRetry = {},
            onOpenMarket = { _, _ -> },
            onRowVisible = { _, _ -> },
            onOpenExplore = {},
            onComingSoon = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun PerpsPortfolioPositionsPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsPortfolioContent(
            portfolio = PerpsPortfolio(
                balance = PerpsBalance(
                    availableBalance = BigDecimal("980.15"),
                    equity = BigDecimal("1865.89"),
                    unrealizedPnlTotal = BigDecimal("103.80"),
                ),
                positions = previewPositions,
            ),
            portfolioLoading = false,
            portfolioError = null,
            sort = PerpsSort.VOLUME,
            exploreMarkets = flowOf(PagingData.from(previewMarkets)).collectAsLazyPagingItems(),
            livePrices = emptyMap(),
            onSortChange = {},
            onRetry = {},
            onOpenMarket = { _, _ -> },
            onRowVisible = { _, _ -> },
            onOpenExplore = {},
            onComingSoon = {},
            onBack = {},
        )
    }
}
