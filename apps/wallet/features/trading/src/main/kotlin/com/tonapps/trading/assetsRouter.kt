package com.tonapps.trading

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
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
    onOpenSwap: (fromToken: String, toToken: String) -> Unit,
    onOpenSend: (tokenAddress: String) -> Unit,
    onOpenReceive: (token: TokenEntity?) -> Unit,
    onOpenTxDetails: (tx: TxEvent, actionIndex: Int) -> Unit,
    onOpenStaking: () -> Unit,
    onOpenUnverifiedInfo: () -> Unit,
    onClose: () -> Unit,
) {
    val backStack = rememberNavBackStack(initial)

    val onBack: () -> Unit = {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        } else {
            onClose()
        }
    }

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
                    onBack = onBack,
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
                    onBack = onBack,
                )
            }

            else -> throw IllegalStateException("Unknown key: $key")
        }
    }
}
