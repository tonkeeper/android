package com.tonapps.trading

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.bus.generated.Events.AssetScreen.AssetScreenFrom
import com.tonapps.wallet.data.events.tx.model.TxEvent
import com.tonapps.trading.screens.assets.AssetsFeature
import com.tonapps.trading.screens.assets.AssetsScreen
import com.tonapps.trading.screens.details.AssetDetailsFeature
import com.tonapps.trading.screens.details.AssetDetailsScreen
import io.tradingapi.models.AssetsTab
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import ui.moon.MoonNav
import ui.moon.rememberNestedNavBackStack

@Serializable
sealed interface AssetsRoutes : NavKey {

    @Serializable
    data class Assets(
        val focusSearch: Boolean = false,
        val initialTab: AssetsTab = AssetsTab.all,
    ) : AssetsRoutes

    @Serializable
    data class AssetDetails(
        val assetId: String,
        val previewName: String,
        val previewImageUrl: String,
        val from: AssetScreenFrom,
    ) : AssetsRoutes
}

@Composable
fun AssetsRouter(
    initial: AssetsRoutes = AssetsRoutes.Assets(),
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
    onOpenAssetHistory: (assetId: String) -> Unit,
    onClose: () -> Unit,
) {
    val backStack = rememberNestedNavBackStack(initial, onClose)

    MoonNav(backStack = backStack) { key ->
        when (key) {
            is AssetsRoutes.Assets -> NavEntry(key) {
                val viewModel = koinViewModel<AssetsFeature> { parametersOf(key.initialTab) }
                AssetsScreen(
                    feature = viewModel,
                    focusSearch = key.focusSearch,
                    initialTab = key.initialTab,
                    onOpenAssetDetails = { asset ->
                        backStack.add(AssetsRoutes.AssetDetails(
                            assetId = asset.id,
                            previewName = asset.name,
                            previewImageUrl = asset.imageUrl,
                            from = AssetScreenFrom.TradeScreen,
                        ))
                    },
                    onBack = { backStack.safeRemoveLastOrNull(key) },
                )
            }

            is AssetsRoutes.AssetDetails -> NavEntry(key) {
                val viewModel = koinViewModel<AssetDetailsFeature> {
                    parametersOf(key.assetId, key.from)
                }
                AssetDetailsScreen(
                    feature = viewModel,
                    previewName = key.previewName,
                    previewImageUrl = key.previewImageUrl,
                    onOpenToken = onOpenToken,
                    onOpenUrl = onOpenUrl,
                    onOpenSwap = onOpenSwap,
                    onOpenSend = onOpenSend,
                    onOpenReceive = onOpenReceive,
                    onOpenTxDetails = onOpenTxDetails,
                    onOpenStaking = onOpenStaking,
                    onOpenUnverifiedInfo = onOpenUnverifiedInfo,
                    onSellToCard = onSellToCard,
                    onBuyWithCard = onBuyWithCard,
                    onSeeAllActivities = { onOpenAssetHistory(key.assetId) },
                    onBack = { backStack.safeRemoveLastOrNull(key) },
                )
            }

            else -> throw IllegalStateException("Unknown key: $key")
        }
    }
}
