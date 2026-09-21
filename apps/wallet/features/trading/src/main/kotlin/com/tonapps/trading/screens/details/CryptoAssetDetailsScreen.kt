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
import io.tradingapi.models.AssetRef.Verification
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
internal fun CryptoAssetDetailsScreen(
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
    onOpenStaking: () -> Unit,
    onOpenUnverifiedInfo: () -> Unit,
    onSellToCard: (assetId: String) -> Unit = {},
    onBuyWithCard: (assetId: String) -> Unit = {},
    onSeeAllActivities: () -> Unit = {},
    onToggleVisibility: () -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onTrackButtonClick: (AssetScreenButton) -> Unit = {},
    onRefresh: () -> Unit = {},
    onBack: () -> Unit,
) {
    val data = state as? AssetDetailsState.Data
    val asset = data?.details?.asset
    val mcAsset = Asset.coinFromString(assetId)
    val isNativeGram = assetId.startsWith("ton/") && assetId.substringAfterLast("/") == "coin"
    val title = if (isNativeGram) {
        stringResource(Localization.toncoin)
    } else {
        asset?.name ?: previewName
    }
    val imageUrl = previewImageUrl.ifBlank { asset?.imageUrl } ?: ""
    val chainImageUrl = mcAsset?.tokenChainImageUrl()
    val chainName = mcAsset?.chain?.coin?.name?.displayChainName()
    val (headerSubtitle, headerSubtitleColor) = when (asset?.verification) {
        null, Verification.whitelist -> chainName to UIKit.colorScheme.text.secondary
        Verification.trusted -> chainName to UIKit.colorScheme.text.secondary
        Verification.none -> stringResource(Localization.unverified_token) to UIKit.colorScheme.accent.orange
        Verification.blacklist -> stringResource(Localization.scam) to UIKit.colorScheme.accent.red
    }
    val showVerifiedBadge = asset?.verification == Verification.trusted
    var showVerifiedModal by remember { mutableStateOf(false) }

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
                subtitle = headerSubtitle,
                subtitleColor = headerSubtitleColor,
                showDivider = showDivider,
                onOpenExplorer = onOpenExplorer,
                onToggleVisibility = onToggleVisibility,
                onToggleFavorite = onToggleFavorite,
                onBack = onBack,
                showVerifiedBadge = showVerifiedBadge,
                onVerifiedBadgeClick = { showVerifiedModal = true },
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
                    CryptoAssetDetailsData(
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
                        maxStakingApyFormatted = data?.maxStakingApyFormatted,
                        showSend = hasBalance,
                        showCashBuy = data?.buyWithCardAvailable == true,
                        showCashSell = hasBalance && data?.sellToCardAvailable == true,
                        onOpenToken = onOpenToken,
                        onOpenUrl = onOpenUrl,
                        onChartPeriodChange = onChartPeriodChange,
                        onOpenTxDetails = onOpenTxDetails,
                        onOpenStaking = onOpenStaking,
                        onOpenUnverifiedInfo = onOpenUnverifiedInfo,
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

    if (showVerifiedModal) {
        VerifiedTokenModal(onDismiss = { showVerifiedModal = false })
    }
}

@Composable
private fun CryptoAssetDetailsData(
    modifier: Modifier = Modifier,
    sections: AssetDetailsSections?,
    asset: AssetRef? = null,
    imageUrl: String,
    chainImageUrl: String?,
    currencyCode: String,
    chartData: List<ChartPoint>?,
    chartPeriod: ChartPeriod,
    showPerps: Boolean = false,
    maxStakingApyFormatted: String? = null,
    showSend: Boolean = false,
    showCashBuy: Boolean = false,
    showCashSell: Boolean = false,
    onOpenToken: (token: TokenEntity, eventsOnly: Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onChartPeriodChange: (ChartPeriod) -> Unit,
    onOpenTxDetails: (tx: TxEvent, actionIndex: Int) -> Unit,
    onOpenStaking: () -> Unit,
    onOpenUnverifiedInfo: () -> Unit = {},
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
            maxStakingApyFormatted = maxStakingApyFormatted,
            currencyCode = currencyCode,
            chartData = chartData,
            chartPeriod = chartPeriod,
            onPeriodChange = onChartPeriodChange,
            onOpenStaking = onOpenStaking,
        )
        CryptoDisclaimerSection(
            asset = asset,
            onOpenUnverifiedInfo = onOpenUnverifiedInfo,
        )
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

        if (showPerps) {
            PerpsSection()
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
private fun CryptoDisclaimerSection(
    asset: AssetRef?,
    onOpenUnverifiedInfo: () -> Unit,
) {
    val isUnverified = asset != null &&
        (asset.verification == Verification.none || asset.verification == Verification.blacklist)
    if (!isUnverified) {
        MoonDivider()
        return
    }

    DisclaimerBanner(
        text = stringResource(Localization.unverified_asset),
        color = UIKit.colorScheme.accent.orange,
        onClick = onOpenUnverifiedInfo,
    )
}

@Preview
@Composable
private fun CryptoAssetDetailsScreenPreview() {
    ThemedPreview {
        MoonScaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(rememberNestedScrollInteropConnection())
                .statusBarsPadding(),
            title = "Toncoin",
            onBack = {},
        ) {
            val bottomOverlayHeight = moonBottomBarHeight()
            Box(modifier = Modifier.fillMaxSize()) {
                CryptoAssetDetailsData(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = bottomOverlayHeight),
                    sections = previewAssetDetailsSections,
                    imageUrl = "",
                    chainImageUrl = null,
                    showPerps = true,
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
                    onOpenStaking = {},
                )
                AssetDetailsBottomBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    hasBalance = true,
                )
            }
        }
    }
}
