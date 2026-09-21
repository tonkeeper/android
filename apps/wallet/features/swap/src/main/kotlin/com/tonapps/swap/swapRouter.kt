package com.tonapps.swap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.bus.generated.Events.MysteryRaffle.MysteryRaffleSource
import com.tonapps.core.deeplink.DeepLinkBuilder
import com.tonapps.core.deeplink.withRaffleSource
import com.tonapps.core.navigation.LocalResultStore
import com.tonapps.core.navigation.rememberResultStore
import com.tonapps.swap.screens.picker.SwapAssetPickerFeature
import com.tonapps.swap.screens.picker.SwapAssetPickerScreen
import com.tonapps.swap.screens.swap.SwapFeature
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import ui.moon.MoonNav
import ui.moon.rememberNestedNavBackStack

@Serializable
sealed interface SwapRoutes : NavKey {
    @Serializable
    data class Swap(
        val sellAssetId: String? = null,
        val buyAssetId: String? = null,
    ) : SwapRoutes

    @Serializable
    data class SelectAsset(val side: Side) : SwapRoutes {
        enum class Side { Send, Receive }
    }
}

@Composable
fun SwapRouter(
    initial: SwapRoutes = SwapRoutes.Swap(),
    onBack: () -> Unit,
    onContinue: (ConfirmRequest) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
) {
    val backStack = rememberNestedNavBackStack(initial, onBack)
    val resultStore = rememberResultStore()
    val swapFeature = koinViewModel<SwapFeature> {
        parametersOf(initial as? SwapRoutes.Swap ?: SwapRoutes.Swap())
    }

    CompositionLocalProvider(LocalResultStore provides resultStore) {
        MoonNav(
            modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection()),
            backStack = backStack,
        ) { key ->
            when (key) {
                is SwapRoutes.Swap -> NavEntry(key) {
                    SwapScreen(
                        feature = swapFeature,
                        onClose = onBack,
                        onSelectSendAsset = {
                            backStack.add(SwapRoutes.SelectAsset(SwapRoutes.SelectAsset.Side.Send))
                        },
                        onSelectReceiveAsset = {
                            backStack.add(SwapRoutes.SelectAsset(SwapRoutes.SelectAsset.Side.Receive))
                        },
                        onContinue = onContinue,
                        onOpenRaffle = { raffleId ->
                            onOpenLink(DeepLinkBuilder.raffle(raffleId).withRaffleSource(MysteryRaffleSource.SwapPromo.key))
                        },
                    )
                }

                is SwapRoutes.SelectAsset -> NavEntry(key) {
                    val pickerFeature = koinViewModel<SwapAssetPickerFeature>(
                        key = "picker-${key.side.name}",
                    ) { parametersOf(key.side) }

                    SwapAssetPickerScreen(
                        feature = pickerFeature,
                        onClose = { onBack() },
                        onBack = { backStack.safeRemoveLastOrNull(key) },
                        onAssetSelected = { asset ->
                            when (key.side) {
                                SwapRoutes.SelectAsset.Side.Send -> swapFeature.selectSellAsset(asset)
                                SwapRoutes.SelectAsset.Side.Receive -> swapFeature.selectBuyAsset(asset)
                            }
                            backStack.safeRemoveLastOrNull(key)
                        },
                    )
                }

                else -> throw IllegalStateException("Unknown key: $key")
            }
        }
    }
}
