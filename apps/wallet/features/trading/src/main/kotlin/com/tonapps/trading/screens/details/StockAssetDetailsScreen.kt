package com.tonapps.trading.screens.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.core.components.tokenChainImageUrl
import com.tonapps.bus.generated.Events.AssetScreen.AssetScreenButton
import com.tonapps.trading.asTokenEntity
import com.tonapps.trading.displayChainName
import com.tonapps.trading.omnistonBuyTokens
import com.tonapps.trading.omnistonSellTokens
import com.tonapps.wallet.data.events.tx.model.TxEvent
import com.tonapps.wallet.localization.Localization
import io.tradingapi.models.AssetRef
import io.tradingapi.models.AssetType
import ui.components.moon.moonBottomBarHeight
import ui.components.moon.container.MoonScaffold
import ui.components.moon.screen.MoonEmptyScreen
import ui.components.moon.screen.MoonEmptyScreenType
import ui.components.moon.MoonDivider
import ui.components.moon.MoonRefresh
import ui.preview.ThemedPreview
import ui.theme.UIKit
import uikit.chart.ChartPeriod
import uikit.chart.ChartPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StockAssetDetailsScreen(
    assetId: String,
    state: AssetDetailsState,
    previewName: String,
    previewImageUrl: String,
    onChartPeriodChange: (ChartPeriod) -> Unit,
    onOpenToken: (token: TokenEntity, eventsOnly: Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenSwap: (fromAssetId: String?, toAssetId: String?, fromToken: String, toToken: String) -> Unit,
    onOpenSend: (assetId: String, tokenAddress: String) -> Unit,
    onOpenReceive: (assetId: String, token: TokenEntity) -> Unit,
    onOpenTxDetails: (tx: TxEvent, actionIndex: Int) -> Unit,
    onOpenExplorer: () -> Unit,
    onSellToCard: (assetId: String) -> Unit = {},
    onBuyWithCard: (assetId: String) -> Unit = {},
    onSeeAllActivities: () -> Unit = {},
    onToggleVisibility: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onTrackButtonClick: (AssetScreenButton) -> Unit = {},
    onRefresh: () -> Unit = {},
    onBack: () -> Unit,
) {
    val data = state as? AssetDetailsState.Data // TODO TK-2103 move to feature
    val asset = data?.details?.asset
    val mcAsset = remember { Asset.coinFromString(assetId) }
    val title = asset?.name ?: previewName
    val imageUrl = previewImageUrl.ifBlank { asset?.imageUrl } ?: ""
    val chainImageUrl = mcAsset?.tokenChainImageUrl()
    val chainName = mcAsset?.chain?.coin?.name?.displayChainName()

    val scrollState = rememberScrollState()
    val showDivider by remember {
        derivedStateOf { scrollState.value > 0 }
    }
    val pullToRefreshState = rememberPullToRefreshState()

    MoonScaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(rememberNestedScrollInteropConnection()),
        topBar = {
            AssetDetailsTopBar(
                state = state,
                title = title,
                subtitle = chainName,
                showDivider = showDivider,
                onOpenExplorer = onOpenExplorer,
                onToggleVisibility = onToggleVisibility,
                onToggleFavorite = onToggleFavorite,
                onBack = onBack,
            )
        },
    ) {
        if (state is AssetDetailsState.Error) {
            MoonEmptyScreen(
                modifier = Modifier.fillMaxSize(),
                type = MoonEmptyScreenType.Error,
                text = stringResource(Localization.cant_find_anything),
            )
        } else {
            val bottomOverlayHeight = moonBottomBarHeight()
            PullToRefreshBox(
                modifier = Modifier.fillMaxSize(),
                isRefreshing = data?.isRefreshing == true,
                onRefresh = onRefresh,
                state = pullToRefreshState,
                indicator = {
                    MoonRefresh(
                        modifier = Modifier.align(Alignment.TopCenter),
                        state = pullToRefreshState,
                    )
                },
            ) {
                val hasBalance = data?.sections?.balance != null
                val isMultichainWallet = data?.isMultichainWallet == true
                Box(modifier = Modifier.fillMaxSize()) {
                    StockAssetDetailsData(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(bottom = bottomOverlayHeight),
                        sections = data?.sections,
                        asset = asset,
                        imageUrl = imageUrl,
                        chainImageUrl = chainImageUrl,
                        currencyCode = data?.currencyCode ?: "",
                        chartData = data?.chartData,
                        chartPeriod = data?.chartPeriod ?: AssetDetailsFeature.InitialChartPeriod,
                        showSend = hasBalance,
                        showCashBuy = isMultichainWallet && data?.buyWithCardAvailable == true,
                        showCashSell = isMultichainWallet && hasBalance && data?.sellToCardAvailable == true,
                        onOpenToken = onOpenToken,
                        onOpenUrl = onOpenUrl,
                        onChartPeriodChange = onChartPeriodChange,
                        onOpenTxDetails = onOpenTxDetails,
                        onSeeAllActivities = onSeeAllActivities,
                        onSendClick = {
                            onTrackButtonClick(AssetScreenButton.Send)
                            asset?.let { onOpenSend(it.id, it.asTokenEntity.address) }
                        },
                        onReceiveClick = {
                            onTrackButtonClick(AssetScreenButton.Receive)
                            asset?.let { onOpenReceive(assetId, it.asTokenEntity) }
                        },
                        onCashBuyClick = { asset?.let { onBuyWithCard(it.id) } },
                        onCashSellClick = { asset?.let { onSellToCard(it.id) } },
                    )
                    if (data != null && data.tradeAvailable) {
                        AssetDetailsBottomBar(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            hasBalance = hasBalance,
                            onBuyClick = {
                                onTrackButtonClick(AssetScreenButton.Buy)
                                val (from, to) = data.details.asset.omnistonBuyTokens()
                                onOpenSwap(null, data.details.asset.id, from, to)
                            },
                            onSellClick = {
                                onTrackButtonClick(AssetScreenButton.Sell)
                                val (from, to) = data.details.asset.omnistonSellTokens()
                                onOpenSwap(data.details.asset.id, null, from, to)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StockAssetDetailsData(
    modifier: Modifier = Modifier,
    sections: AssetDetailsSections?,
    asset: AssetRef? = null,
    imageUrl: String,
    chainImageUrl: String?,
    currencyCode: String,
    chartData: List<ChartPoint>?,
    chartPeriod: ChartPeriod,
    showSend: Boolean = false,
    showCashBuy: Boolean = false,
    showCashSell: Boolean = false,
    onOpenToken: (token: TokenEntity, eventsOnly: Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onChartPeriodChange: (ChartPeriod) -> Unit,
    onOpenTxDetails: (tx: TxEvent, actionIndex: Int) -> Unit,
    onSeeAllActivities: () -> Unit = {},
    onSendClick: () -> Unit = {},
    onReceiveClick: () -> Unit = {},
    onCashBuyClick: () -> Unit = {},
    onCashSellClick: () -> Unit = {},
) {
    Column(modifier = modifier) {
        ChartSection(
            imageUrl = imageUrl,
            chainImageUrl = chainImageUrl,
            currencyCode = currencyCode,
            chartData = chartData,
            chartPeriod = chartPeriod,
            onPeriodChange = onChartPeriodChange,
            onOpenStaking = {},
        )
        StockDisclaimerSection(asset = asset)
        if (sections != null) {
            if (sections.balance != null) {
                DetailsBalanceSection(
                    balance = sections.balance,
                    imageUrl = imageUrl,
                    chainImageUrl = chainImageUrl,
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }
            AssetActionButtonsRow(
                showSend = showSend,
                showCashBuy = showCashBuy,
                showCashSell = showCashSell,
                onSendClick = onSendClick,
                onReceiveClick = onReceiveClick,
                onCashBuyClick = onCashBuyClick,
                onCashSellClick = onCashSellClick,
            )
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            DetailsBalanceSectionShimmer()
        }

        sections?.recentEvents?.let {
            EventsSection(
                recentEvents = it,
                onOpenToken = onOpenToken,
                onOpenTxDetails = onOpenTxDetails,
            )
        }
        sections?.recentActivities?.let {
            ActivitiesSection(
                recentActivities = it,
                onSeeAllClick = onSeeAllActivities,
            )
        }
        sections?.about?.let { DetailsAboutSection(about = it) }
        sections?.overview?.let { DetailsOverviewSection(overview = it) }
        sections?.trading?.let { DetailsTradingSection(trading = it, onOpenUrl = onOpenUrl) }
        sections?.links?.let { DetailsLinksSection(links = it, onOpenUrl = onOpenUrl) }
    }
}

@Composable
private fun StockDisclaimerSection(
    asset: AssetRef?,
) {
    val text = when (asset?.assetType) {
        AssetType.stocks -> stringResource(Localization.tokenized_stocks)
        AssetType.etfs -> stringResource(Localization.tokenized_etfs)
        else -> null
    }

    if (text == null || asset == null) {
        MoonDivider()
        return
    }

    var infoModalType by remember { mutableStateOf<AssetInfoModalType?>(null) }

    DisclaimerBanner(
        text = text,
        color = UIKit.colorScheme.text.accent,
        onClick = {
            infoModalType = when (asset.assetType) {
                AssetType.stocks -> AssetInfoModalType.Stock
                AssetType.etfs -> AssetInfoModalType.Etf
                else -> null
            }
        },
    )

    infoModalType?.let { modalType ->
        AssetDetailsInfoModal(
            type = modalType,
            onDismiss = { infoModalType = null },
        )
    }
}

@Preview
@Composable
private fun StockAssetDetailsScreenPreview() {
    ThemedPreview {
        MoonScaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(rememberNestedScrollInteropConnection())
                .statusBarsPadding(),
            title = "Apple Inc.",
            onBack = {},
        ) {
            val bottomOverlayHeight = moonBottomBarHeight()
            Box(modifier = Modifier.fillMaxSize()) {
                StockAssetDetailsData(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = bottomOverlayHeight),
                    sections = previewAssetDetailsSections,
                    imageUrl = "",
                    chainImageUrl = null,
                    currencyCode = "USD",
                    chartData = previewChartData,
                    chartPeriod = ChartPeriod.month,
                    showSend = true,
                    showCashBuy = true,
                    showCashSell = true,
                    onOpenToken = { _, _ -> },
                    onOpenUrl = {},
                    onChartPeriodChange = {},
                    onOpenTxDetails = { _, _ -> },
                )
                AssetDetailsBottomBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    hasBalance = true,
                )
            }
        }
    }
}
