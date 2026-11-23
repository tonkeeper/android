package com.tonapps.swap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.tonapps.blockchain.model.ConfirmInitiator
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.bus.generated.Events.MysteryRaffle.MysteryRaffleSource
import com.tonapps.core.LocalIsTopFragment
import com.tonapps.core.deeplink.DeepLinkBuilder
import com.tonapps.core.deeplink.withRaffleSource
import com.tonapps.core.navigation.LocalResultStore
import com.tonapps.core.navigation.rememberResultStore
import com.tonapps.deposit.multicoin.screens.confirm.HeadlessSwapConfirm
import com.tonapps.deposit.multicoin.screens.confirm.InsufficientFundsDialog
import com.tonapps.swap.screens.picker.SwapAssetPickerFeature
import com.tonapps.swap.screens.picker.SwapAssetPickerScreen
import com.tonapps.swap.screens.swap.SwapAction
import com.tonapps.swap.screens.swap.SwapFeature
import com.tonapps.swap.screens.swap.SwapSendHost
import com.tonapps.swap.screens.swap.SwapSendPhase
import com.tonapps.swap.screens.swap.SwapSettingsSheet
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import ui.components.moon.dialog.MoonDialogDisposableScope
import ui.moon.MoonNav
import ui.moon.rememberNestedNavBackStack

@Serializable
sealed interface SwapRoutes : NavKey {
    @Serializable
    data class Swap(
        val sellAssetId: String? = null,
        val buyAssetId: String? = null,
        val initiator: ConfirmInitiator = ConfirmInitiator.User,
        val utm: UtmParameters = UtmParameters.Empty,
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
    onSwapCompleted: (tracked: Boolean) -> Unit = {},
    onOpenLink: (String) -> Unit = {},
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
    onTopUp: (request: ConfirmRequest, assetId: String) -> Unit,
) {
    val backStack = rememberNestedNavBackStack(initial, onBack)
    val resultStore = rememberResultStore()
    val swapFeature = koinViewModel<SwapFeature> {
        parametersOf(initial as? SwapRoutes.Swap ?: SwapRoutes.Swap())
    }
    val send by swapFeature.send.collectAsState()
    val current = remember { mutableStateOf<HeadlessSwapConfirm?>(null) }

    val isTopFragment = LocalIsTopFragment.current
    var wasCovered by remember { mutableStateOf(false) }
    LaunchedEffect(isTopFragment) {
        if (!isTopFragment) {
            wasCovered = true
        } else if (wasCovered) {
            wasCovered = false
            if (send?.phase == SwapSendPhase.Review) {
                current.value?.retryFees()
            }
        }
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
                        onSwapCompleted = onSwapCompleted,
                        onOpenRaffle = { raffleId ->
                            onOpenLink(DeepLinkBuilder.raffle(raffleId).withRaffleSource(MysteryRaffleSource.SwapPromo.key))
                        },
                        onOpenBattery = onOpenBattery,
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
                                SwapRoutes.SelectAsset.Side.Send -> swapFeature.sendAction(SwapAction.Asset.SelectSell(asset))
                                SwapRoutes.SelectAsset.Side.Receive -> swapFeature.sendAction(SwapAction.Asset.SelectBuy(asset))
                            }
                            backStack.safeRemoveLastOrNull(key)
                        },
                    )
                }

                else -> throw IllegalStateException("Unknown key: $key")
            }
        }

        send?.let { attempt ->
            key(attempt.id) {
                MoonDialogDisposableScope {
                    SwapSendHost(attempt = attempt, feature = swapFeature, current = current)
                }
            }
            val prepared = current.value?.prepared?.collectAsState()?.value
            if (attempt.shortagePopup && prepared != null) {
                prepared.InsufficientFundsDialog(
                    onTopUp = { assetId ->
                        swapFeature.sendAction(SwapAction.CloseShortage)
                        onTopUp(attempt.request, assetId)
                    },
                    onOpenBattery = { walletId, from ->
                        swapFeature.sendAction(SwapAction.CloseShortage)
                        onOpenBattery(walletId, from)
                    },
                    onClose = { swapFeature.sendAction(SwapAction.CloseShortage) },
                )
            }
            if (attempt.inSheet) {
                SwapSettingsSheet(
                    feature = swapFeature,
                    confirm = current.value,
                    onOpenBattery = { walletId, from ->
                        swapFeature.sendAction(SwapAction.CloseDetails)
                        onOpenBattery(walletId, from)
                    },
                    onTopUp = { assetId ->
                        swapFeature.sendAction(SwapAction.CloseDetails)
                        onTopUp(attempt.request, assetId)
                    },
                )
            }
        }
    }
}
