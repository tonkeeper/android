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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.tonapps.core.LocalIsTopFragment
import com.tonapps.paging.collectAsStateWorkaround
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLoadException
import com.tonapps.perps.data.PerpsPortfolioOrder
import com.tonapps.perps.data.PerpsPosition
import com.tonapps.perps.data.PerpsPositionSide
import com.tonapps.perps.data.PerpsSort
import com.tonapps.perps.data.perpsTicker
import com.tonapps.perps.data.withLivePrice
import com.tonapps.perps.screens.common.openPerpsBasics
import com.tonapps.perps.screens.markets.PerpMarketRow
import com.tonapps.perps.screens.markets.PerpsSortSelector
import com.tonapps.perps.screens.markets.perpsChangeColor
import com.tonapps.perps.screens.markets.perpsErrorDescription
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.flowOf
import ui.components.moon.ButtonColorsPrimary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.ButtonSizeSmall
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonBottomBar
import ui.components.moon.MoonCircleIcon
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemSubtitle
import ui.components.moon.MoonLoader
import ui.components.moon.MoonSmallItemTitle
import ui.components.moon.MoonTopAppBar
import ui.components.moon.MoonTopAppBarIcon
import ui.components.moon.MoonTopAppBarSubtitle
import ui.components.moon.MoonTopAppBarTitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.MoonBundleTitleCell
import ui.components.moon.cell.defaultBundleType
import ui.components.moon.cell.MoonCardCell
import ui.components.moon.cell.MoonLoaderCell
import ui.components.moon.cell.MoonRetryCell
import ui.components.moon.container.MoonScaffold
import ui.components.moon.moonBottomBarHeight
import ui.preview.ThemedPreview
import ui.theme.UIKit
import java.math.BigDecimal

@Composable
fun PerpsPortfolioScreen(
    feature: PerpsPortfolioFeature,
    onOpenMarket: (marketIndex: Int, symbol: String) -> Unit,
    onOpenExplore: () -> Unit,
    onDeposit: () -> Unit,
    onOpenHistory: () -> Unit,
    onWithdraw: () -> Unit,
    onBack: () -> Unit,
) {
    val portfolio by feature.portfolio.collectAsStateWorkaround()
    val portfolioLoading by feature.portfolioLoading.collectAsStateWorkaround()
    val portfolioError by feature.portfolioError.collectAsStateWorkaround()
    val sort by feature.sort.collectAsStateWorkaround()
    val exploreMarkets = feature.exploreMarkets.collectAsLazyPagingItems()
    val livePrices by feature.livePrices.collectAsStateWorkaround()
    val fundingStatus by feature.fundingStatus.collectAsStateWorkaround()

    val context = LocalContext.current

    val isTopFragment = LocalIsTopFragment.current
    LifecycleResumeEffect(feature, isTopFragment) {
        if (isTopFragment) {
            feature.sendAction(PerpsPortfolioAction.SetActive(true))
        }
        onPauseOrDispose {
            feature.sendAction(PerpsPortfolioAction.SetActive(false))
        }
    }

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
        onDeposit = onDeposit,
        onWithdraw = onWithdraw,
        fundingStatus = fundingStatus,
        onFundingPillClick = {
            when (fundingStatus) {
                PerpsFundingStatus.NEEDS_AUTHORIZATION ->
                    feature.sendAction(PerpsPortfolioAction.AuthorizeDeposit(context))

                PerpsFundingStatus.FAILED,
                PerpsFundingStatus.REFUNDED,
                PerpsFundingStatus.WITHDRAW_FAILED -> feature.sendAction(PerpsPortfolioAction.DismissFundingOutcome)

                PerpsFundingStatus.SENDING,
                PerpsFundingStatus.CONFIRMED,
                null -> Unit
            }
        },
        onOpenHistory = onOpenHistory,
        onLearnBasics = { context.openPerpsBasics() },
        onBack = onBack,
    )
}

@Composable
private fun PerpsPortfolioContent(
    portfolio: PerpsPortfolioUi,
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
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
    fundingStatus: PerpsFundingStatus?,
    onFundingPillClick: () -> Unit,
    onOpenHistory: () -> Unit,
    onLearnBasics: () -> Unit,
    onBack: () -> Unit,
) {
    val positions = portfolio.positions
    val isZeroState = !portfolioLoading && portfolioError == null && portfolio.isEmpty

    MoonScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Box(modifier = Modifier.statusBarsPadding()) {
                MoonTopAppBar(
                    title = { MoonTopAppBarTitle(text = stringResource(Localization.perps_title)) },
                    onTitleClick = onLearnBasics.takeIf { fundingStatus == null },
                    textVisible = fundingStatus == null,
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
                        if (!isZeroState) {
                            MoonTopAppBarIcon(
                                painter = painterResource(UIKitIcon.ic_clock_outline_28),
                                onClick = onOpenHistory,
                            )
                        }
                        MoonTopAppBarIcon(
                            painter = painterResource(UIKitIcon.ic_magnifying_glass_16),
                            onClick = onOpenExplore,
                        )
                    },
                    backgroundColor = Color.Transparent,
                )
                if (fundingStatus != null) {
                    FundingStatusPill(
                        status = fundingStatus,
                        modifier = Modifier.align(Alignment.Center),
                        onClick = onFundingPillClick.takeIf { fundingStatus.actionable },
                    )
                }
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                contentPadding = if (isZeroState) {
                    PaddingValues(top = 8.dp, bottom = moonBottomBarHeight())
                } else {
                    PaddingValues(
                        top = 8.dp,
                        bottom = WindowInsets.navigationBars.asPaddingValues()
                            .calculateBottomPadding(),
                    )
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
                            balanceFormat = portfolio.balanceFormat,
                            funded = portfolio.funded,
                            onDeposit = onDeposit,
                            onWithdraw = onWithdraw,
                        )
                    }
                }

                item(key = "balance_gap") {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (positions.isNotEmpty()) {
                    item(key = "positions_title") {
                        MoonBundleTitleCell(title = stringResource(Localization.perps_open_positions))
                    }
                    item(key = "positions_total") {
                        PerpsPositionsTotalCard(total = portfolio.positionsTotal)
                    }
                    itemsIndexed(
                        items = positions,
                        key = { _, item -> "position_${item.position.marketIndex}_${item.position.side}" },
                    ) { index, item ->
                        MoonBundleCell(
                            position = if (index == positions.lastIndex) {
                                MoonBundlePosition.Footer
                            } else {
                                MoonBundlePosition.Middle
                            },
                            onClick = { onOpenMarket(item.position.marketIndex, item.position.symbol) },
                        ) {
                            PerpsPositionRow(item = item, modifier = Modifier.padding(16.dp))
                        }
                    }
                    item(key = "positions_gap") {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                val openOrders = portfolio.orders
                if (openOrders.isNotEmpty()) {
                    item(key = "orders_title") {
                        MoonBundleTitleCell(title = stringResource(Localization.perps_limit_orders))
                    }
                    itemsIndexed(
                        items = openOrders,
                        key = { _, item -> "order_${item.order.marketIndex}_${item.order.orderIndex}" },
                    ) { index, item ->
                        MoonBundleCell(
                            position = defaultBundleType(openOrders.size, index),
                            onClick = { onOpenMarket(item.order.marketIndex, item.order.symbol) },
                        ) {
                            PerpsPortfolioOrderRow(item = item, modifier = Modifier.padding(16.dp))
                        }
                    }
                    item(key = "orders_gap") {
                        Spacer(modifier = Modifier.height(16.dp))
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
                        onClick = onDeposit,
                    )
                }
            }
        }
    }
}

@Composable
private fun FundingStatusPill(
    status: PerpsFundingStatus,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(UIKit.colorScheme.background.contentTint)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(start = 16.dp, top = 14.dp, end = 24.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (status) {
            PerpsFundingStatus.SENDING -> MoonLoader(
                modifier = Modifier.size(16.dp),
                color = UIKit.colorScheme.icon.secondary,
            )

            PerpsFundingStatus.CONFIRMED -> MoonItemIcon(
                painter = painterResource(UIKitIcon.ic_checkmark_circle_16),
                size = 16.dp,
                color = UIKit.colorScheme.accent.green,
            )

            PerpsFundingStatus.NEEDS_AUTHORIZATION -> MoonItemIcon(
                painter = painterResource(UIKitIcon.ic_information_circle_16),
                size = 16.dp,
                color = UIKit.colorScheme.accent.orange,
            )

            PerpsFundingStatus.FAILED,
            PerpsFundingStatus.WITHDRAW_FAILED -> MoonItemIcon(
                painter = painterResource(UIKitIcon.ic_xmark_circle_16),
                size = 16.dp,
                color = UIKit.colorScheme.accent.red,
            )

            PerpsFundingStatus.REFUNDED -> MoonItemIcon(
                painter = painterResource(UIKitIcon.ic_exclamationmark_circle_16),
                size = 16.dp,
                color = UIKit.colorScheme.accent.orange,
            )
        }
        MoonSmallItemTitle(
            text = stringResource(
                when (status) {
                    PerpsFundingStatus.SENDING -> Localization.sending_transaction
                    PerpsFundingStatus.CONFIRMED -> Localization.transaction_confirmed
                    PerpsFundingStatus.NEEDS_AUTHORIZATION -> Localization.perps_deposit_confirm_pending
                    PerpsFundingStatus.FAILED -> Localization.perps_deposit_failed
                    PerpsFundingStatus.REFUNDED -> Localization.perps_deposit_refunded
                    PerpsFundingStatus.WITHDRAW_FAILED -> Localization.perps_withdraw_failed
                },
            ),
        )
    }
}

@Composable
private fun BalanceActionButton(
    iconRes: Int,
    background: Color,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        MoonItemIcon(
            painter = painterResource(iconRes),
            size = 28.dp,
            color = tint,
        )
    }
}

@Composable
private fun PerpsBalanceCard(
    balanceFormat: String,
    funded: Boolean,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
) {
    MoonCardCell(
        image = {
            MoonCircleIcon(
                painter = painterResource(UIKitIcon.ic_perps_28),
                color = UIKit.colorScheme.accent.blue.copy(alpha = 0.12f),
                size = 44.dp,
            )
        },
        title = balanceFormat,
        subtitle = stringResource(Localization.perps_balance),
        content = {
            if (funded) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BalanceActionButton(
                        iconRes = UIKitIcon.ic_minus_outline_28,
                        background = UIKit.colorScheme.background.contentTint,
                        tint = UIKit.colorScheme.icon.primary,
                        onClick = onWithdraw,
                    )
                    BalanceActionButton(
                        iconRes = UIKitIcon.ic_plus_outline_28,
                        background = UIKit.colorScheme.buttonPrimary.primaryBackground,
                        tint = UIKit.colorScheme.buttonPrimary.primaryForeground,
                        onClick = onDeposit,
                    )
                }
            } else {
                MoonAccentButton(
                    text = stringResource(Localization.perps_deposit),
                    size = ButtonSizeSmall,
                    onClick = onDeposit,
                )
            }
        },
    )
}

@Composable
private fun PerpsPositionsTotalCard(total: PerpsPositionsTotalUi) {
    MoonBundleCell(position = MoonBundlePosition.Header) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            MoonItemSubtitle(stringResource(Localization.perps_total_amount))
            Text(
                text = total.amountFormat,
                style = UIKit.typography.h2,
                color = UIKit.colorScheme.text.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = total.pnlFormat,
                    style = UIKit.typography.body2,
                    color = perpsChangeColor(total.pnl),
                )
                if (total.pnlPercentFormat != null) {
                    Text(
                        text = total.pnlPercentFormat,
                        style = UIKit.typography.body2,
                        color = perpsChangeColor(total.pnlPercent).copy(alpha = 0.48f),
                    )
                }
            }
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
    PerpsPositionUi(
        position = PerpsPosition(
            marketIndex = 1,
            symbol = "BTC",
            iconUrl = null,
            side = PerpsPositionSide.LONG,
            margin = BigDecimal("20.00"),
            unrealizedPnl = BigDecimal("20.50"),
            equity = BigDecimal("40.50"),
            leverage = 27,
        ),
        equityFormat = "$40.50",
        pnl = BigDecimal("20.50"),
        pnlFormat = "+$20.50",
    ),
    PerpsPositionUi(
        position = PerpsPosition(
            marketIndex = 2,
            symbol = "ETH",
            iconUrl = null,
            side = PerpsPositionSide.SHORT,
            margin = BigDecimal("12.00"),
            unrealizedPnl = BigDecimal("-4.20"),
            equity = BigDecimal("7.80"),
            leverage = 10,
        ),
        equityFormat = "$7.80",
        pnl = BigDecimal("-4.20"),
        pnlFormat = "−$4.20",
    ),
)

private val previewOrders = listOf(
    PerpsOrderUi(
        order = PerpsPortfolioOrder(
            marketIndex = 1,
            orderIndex = 281474976712112L,
            clientOrderIndex = null,
            symbol = "BTC",
            iconUrl = null,
            side = PerpsPositionSide.LONG,
            remainingBase = BigDecimal("0.00150"),
            limitPrice = BigDecimal("64200.00"),
            leverage = 10,
            margin = BigDecimal("9.63"),
        ),
        amountFormat = "$9.63",
        limitPriceFormat = "$64,200.00",
    ),
    PerpsOrderUi(
        order = PerpsPortfolioOrder(
            marketIndex = 2,
            orderIndex = 281474976712113L,
            clientOrderIndex = null,
            symbol = "ETH",
            iconUrl = null,
            side = PerpsPositionSide.SHORT,
            remainingBase = BigDecimal("0.0250"),
            limitPrice = BigDecimal("3620.40"),
            leverage = 5,
            margin = BigDecimal("18.10"),
        ),
        amountFormat = "$18.10",
        limitPriceFormat = "$3,620.40",
    ),
)

private val previewEmptyTotal = PerpsPositionsTotalUi(
    amountFormat = "$0.00",
    pnl = BigDecimal.ZERO,
    pnlFormat = "$0.00",
    pnlPercent = null,
    pnlPercentFormat = "—",
)

private val previewZeroPortfolio = PerpsPortfolioUi(
    balanceFormat = "$0.00",
    funded = false,
    isEmpty = true,
    positions = emptyList(),
    positionsTotal = previewEmptyTotal,
    orders = emptyList(),
)

private val previewFundedPortfolio = previewZeroPortfolio.copy(
    balanceFormat = "$1,762.09",
    funded = true,
    isEmpty = false,
)

@Preview
@Composable
private fun PerpsPortfolioZeroPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsPortfolioContent(
            portfolio = previewZeroPortfolio,
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
            onDeposit = {},
            onWithdraw = {},
            fundingStatus = null,
            onFundingPillClick = {},
            onOpenHistory = {},
            onLearnBasics = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun PerpsPortfolioFundedPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsPortfolioContent(
            portfolio = previewFundedPortfolio,
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
            onDeposit = {},
            onWithdraw = {},
            fundingStatus = null,
            onFundingPillClick = {},
            onOpenHistory = {},
            onLearnBasics = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun PerpsPortfolioPositionsPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsPortfolioContent(
            portfolio = previewFundedPortfolio.copy(
                positions = previewPositions,
                positionsTotal = PerpsPositionsTotalUi(
                    amountFormat = "$48.30",
                    pnl = BigDecimal("16.30"),
                    pnlFormat = "+$16.30",
                    pnlPercent = BigDecimal("50.94"),
                    pnlPercentFormat = "+50.94%",
                ),
                orders = previewOrders,
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
            onDeposit = {},
            onWithdraw = {},
            fundingStatus = null,
            onFundingPillClick = {},
            onOpenHistory = {},
            onLearnBasics = {},
            onBack = {},
        )
    }
}
