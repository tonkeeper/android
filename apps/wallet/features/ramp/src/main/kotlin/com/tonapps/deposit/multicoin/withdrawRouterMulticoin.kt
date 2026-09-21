package com.tonapps.deposit.multicoin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.tonapps.bus.generated.Events.BatteryNative.BatteryNativeFrom
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowFrom
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowWithdrawOption
import com.tonapps.blockchain.utils.isWeb3DomainName
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.core.navigation.LocalResultStore
import com.tonapps.core.navigation.rememberResultStore
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.deposit.multicoin.analytics.WithdrawAnalytics
import com.tonapps.deposit.multicoin.screens.confirm.ConfirmScreen
import com.tonapps.deposit.multicoin.screens.picker.AssetPickerFeature
import com.tonapps.deposit.multicoin.screens.picker.AssetPickerScreen
import com.tonapps.deposit.multicoin.screens.send.SendData
import com.tonapps.deposit.multicoin.screens.send.SendFeature
import com.tonapps.deposit.multicoin.screens.send.SendScreen
import com.tonapps.deposit.multicoin.screens.send.SendTxInfo
import com.tonapps.scanner.ScannerScreen
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import ui.moon.MoonNav
import ui.moon.rememberNestedNavBackStack

private const val KEY_SCANNER_RESULT = "mc_withdraw_scanner_result"

@Serializable
sealed interface WithdrawMulticoinRoutes : NavKey {

    @Serializable
    data class Picker(
        val presetAddress: String? = null,
        val presetComment: String? = null,
    ) : WithdrawMulticoinRoutes

    @Serializable
    data class Send(
        val assetId: String,
        val presetAddress: String? = null,
        val presetAmount: String? = null,
        val presetComment: String? = null,
    ) : WithdrawMulticoinRoutes

    @Serializable
    data object Confirm : WithdrawMulticoinRoutes

    @Serializable
    data object Scanner : WithdrawMulticoinRoutes
}

@Composable
fun WithdrawMulticoinRouter(
    initial: WithdrawMulticoinRoutes,
    onBack: () -> Unit,
    onTopUp: (assetId: String) -> Unit,
    onOpenBattery: (walletId: String, from: BatteryNativeFrom) -> Unit,
    onSendSuccess: () -> Unit = onBack,
    analyticsFrom: WithdrawFlowFrom = WithdrawFlowFrom.WalletScreen,
    onAddressBook: ((onResult: (String) -> Unit) -> Unit)? = null,
) {
    val backStack = rememberNestedNavBackStack(initial, onBack)
    val resultStore = rememberResultStore()
    var confirmRequest by remember { mutableStateOf<ConfirmRequest?>(null) }
    var confirmTxInfo by remember { mutableStateOf<SendTxInfo?>(null) }
    val analytics = remember(analyticsFrom) { WithdrawAnalytics(analyticsFrom) }

    LaunchedEffect(analytics) {
        analytics.started(listOf(WithdrawFlowWithdrawOption.SendTokens))
        analytics.optionClick()
    }

    CompositionLocalProvider(LocalResultStore provides resultStore) {
        MoonNav(
            backStack = backStack,
        ) { key ->
            when (key) {
                is WithdrawMulticoinRoutes.Picker -> NavEntry(key) {
                    val feature = koinViewModel<AssetPickerFeature> {
                        parametersOf(analyticsFrom)
                    }
                    val group = remember(key.presetAddress) {
                        val presetAddress = key.presetAddress
                        when {
                            presetAddress == null -> null
                            presetAddress.isWeb3DomainName() -> Chain.Ton.Mainnet.network.group
                            else -> Address.findChainsByAddress(presetAddress).firstOrNull()?.network?.group
                        }
                    }

                    AssetPickerScreen(
                        feature = feature,
                        group = group,
                        onClose = onBack,
                        onAssetSelected = { account ->
                            analytics.clickAsset(account.asset.id)
                            backStack.add(
                                WithdrawMulticoinRoutes.Send(
                                    assetId = account.asset.id,
                                    presetAddress = key.presetAddress,
                                    presetComment = key.presetComment,
                                )
                            )
                        },
                    )
                }

                is WithdrawMulticoinRoutes.Send -> NavEntry(key) {
                    val feature = koinViewModel<SendFeature> {
                        parametersOf(
                            SendData(
                                initAssetId = key.assetId,
                                presetAddress = key.presetAddress,
                                analyticsFrom = analyticsFrom,
                                presetAmount = key.presetAmount,
                                presetComment = key.presetComment,
                            )
                        )
                    }

                    SendScreen(
                        feature = feature,
                        onClose = onBack,
                        onBack = { backStack.safeRemoveLastOrNull(key) },
                        scannerResult = resultStore.removeResult(KEY_SCANNER_RESULT),
                        onNavigateToScanner = {
                            backStack.add(WithdrawMulticoinRoutes.Scanner)
                        },
                        onAddressBook = onAddressBook,
                        onContinue = { request, txInfo ->
                            confirmRequest = request
                            confirmTxInfo = txInfo
                            backStack.add(WithdrawMulticoinRoutes.Confirm)
                        },
                    )
                }

                is WithdrawMulticoinRoutes.Confirm -> NavEntry(key) {
                    val request = confirmRequest ?: run {
                        backStack.safeRemoveLastOrNull(key)
                        return@NavEntry
                    }
                    val txInfo = confirmTxInfo

                    ConfirmScreen(
                        request = request,
                        onClose = onBack,
                        onSendSuccess = {
                            txInfo?.let {
                                analytics.sendSuccess(it.assetId, it.symbol, it.amount)
                            }
                            onSendSuccess()
                        },
                        onBack = { backStack.safeRemoveLastOrNull(key) },
                        onConfirm = {
                            txInfo?.let {
                                analytics.sendConfirm(it.assetId, it.symbol, it.amount)
                            }
                        },
                        onTopUp = onTopUp,
                        onOpenBattery = onOpenBattery,
                    )
                }

                is WithdrawMulticoinRoutes.Scanner -> NavEntry(key) {
                    ScannerScreen(
                        onResult = { value ->
                            resultStore.setResult(KEY_SCANNER_RESULT, value)
                            backStack.safeRemoveLastOrNull(key)
                        },
                        onClose = { backStack.safeRemoveLastOrNull(key) },
                    )
                }

                else -> throw IllegalStateException("Unknown key: $key")
            }
        }
    }
}
