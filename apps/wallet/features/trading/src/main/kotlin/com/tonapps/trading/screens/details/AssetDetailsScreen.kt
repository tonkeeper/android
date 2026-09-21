package com.tonapps.trading.screens.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.mvi.props.observeSafeState
import com.tonapps.trading.isTokenized
import com.tonapps.wallet.data.events.tx.model.TxEvent

@Composable
fun AssetDetailsScreen(
    feature: AssetDetailsFeature,
    previewName: String,
    previewImageUrl: String,
    onOpenToken: (token: TokenEntity, eventsOnly: Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenSwap: (fromAssetId: String?, toAssetId: String?, fromToken: String, toToken: String) -> Unit,
    onOpenSend: (assetId: String, tokenAddress: String) -> Unit,
    onOpenReceive: (assetId: String, token: TokenEntity) -> Unit,
    onOpenTxDetails: (tx: TxEvent, actionIndex: Int) -> Unit,
    onOpenStaking: () -> Unit,
    onOpenUnverifiedInfo: () -> Unit,
    onSellToCard: (assetId: String) -> Unit,
    onBuyWithCard: (assetId: String) -> Unit,
    onSeeAllActivities: () -> Unit,
    onBack: () -> Unit,
) {
    val state by feature.state.global.observeSafeState()

    LaunchedEffect(feature) {
        feature.events.collect { event ->
            when (event) {
                is AssetDetailsEvent.OpenUrl -> onOpenUrl(event.url)
            }
        }
    }

    val asset = (state as? AssetDetailsState.Data)?.details?.asset

    if (asset?.isTokenized() == true) {
        StockAssetDetailsScreen(
            assetId = feature.assetId,
            state = state,
            previewName = previewName,
            previewImageUrl = previewImageUrl,
            onChartPeriodChange = { feature.sendAction(AssetDetailsAction.SetChartPeriod(it)) },
            onOpenToken = onOpenToken,
            onOpenUrl = onOpenUrl,
            onOpenSwap = onOpenSwap,
            onOpenSend = onOpenSend,
            onOpenReceive = onOpenReceive,
            onOpenTxDetails = onOpenTxDetails,
            onOpenExplorer = { feature.sendAction(AssetDetailsAction.OpenTokenExplorer) },
            onSellToCard = onSellToCard,
            onBuyWithCard = onBuyWithCard,
            onSeeAllActivities = onSeeAllActivities,
            onToggleVisibility = { feature.sendAction(AssetDetailsAction.ToggleVisibility) },
            onToggleFavorite = { feature.sendAction(AssetDetailsAction.ToggleFavorite) },
            onTrackButtonClick = { feature.trackButtonClick(it) },
            onRefresh = { feature.sendAction(AssetDetailsAction.PullToRefresh) },
            onBack = onBack,
        )
    } else {
        CryptoAssetDetailsScreen(
            assetId = feature.assetId,
            state = state,
            previewName = previewName,
            previewImageUrl = previewImageUrl,
            onChartPeriodChange = { feature.sendAction(AssetDetailsAction.SetChartPeriod(it)) },
            onOpenToken = onOpenToken,
            onOpenUrl = onOpenUrl,
            onOpenSwap = onOpenSwap,
            onOpenSend = onOpenSend,
            onOpenReceive = onOpenReceive,
            onOpenTxDetails = onOpenTxDetails,
            onOpenExplorer = { feature.sendAction(AssetDetailsAction.OpenTokenExplorer) },
            onOpenStaking = onOpenStaking,
            onOpenUnverifiedInfo = onOpenUnverifiedInfo,
            onSellToCard = onSellToCard,
            onBuyWithCard = onBuyWithCard,
            onSeeAllActivities = onSeeAllActivities,
            onToggleVisibility = { feature.sendAction(AssetDetailsAction.ToggleVisibility) },
            onToggleFavorite = { feature.sendAction(AssetDetailsAction.ToggleFavorite) },
            onTrackButtonClick = { feature.trackButtonClick(it) },
            onRefresh = { feature.sendAction(AssetDetailsAction.PullToRefresh) },
            onBack = onBack,
        )
    }
}
