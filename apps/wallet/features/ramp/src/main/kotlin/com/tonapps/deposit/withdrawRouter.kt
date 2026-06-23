package com.tonapps.deposit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowFrom
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowSellAsset
import com.tonapps.core.navigation.LocalResultStore
import com.tonapps.core.navigation.rememberResultStore
import com.tonapps.deposit.data.AssetFilter
import com.tonapps.deposit.screens.assets.AssetsCryptoExtendedFeature
import com.tonapps.deposit.screens.assets.AssetsCryptoExtendedFeatureData
import com.tonapps.deposit.screens.assets.AssetsExtendedScreen
import com.tonapps.deposit.screens.confirm.ConfirmFeature
import com.tonapps.deposit.screens.confirm.SendConfirmScreen
import com.tonapps.deposit.screens.currency.SelectCurrencyFeature
import com.tonapps.deposit.screens.currency.SelectCurrencyScreen
import com.tonapps.deposit.screens.method.KEY_CURRENCY_SELECTION_RESULT
import com.tonapps.deposit.screens.method.PaymentMethodFeature
import com.tonapps.deposit.screens.method.PaymentMethodFeatureData
import com.tonapps.deposit.screens.method.PaymentMethodScreen
import com.tonapps.deposit.screens.method.PaymentMethodSectionFilter
import com.tonapps.deposit.screens.method.RampAsset
import com.tonapps.deposit.screens.network.SelectNetworkData
import com.tonapps.deposit.screens.network.SelectNetworkFeature
import com.tonapps.deposit.screens.network.SelectNetworkScreen
import com.tonapps.deposit.screens.picker.TokenPickerFeature
import com.tonapps.deposit.screens.picker.TokenPickerScreen
import com.tonapps.deposit.screens.ramp.RampFeature
import com.tonapps.deposit.screens.ramp.RampScreen
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.deposit.screens.ramp.amount.DepositAmountFeature
import com.tonapps.deposit.screens.ramp.amount.DepositAmountScreen
import com.tonapps.deposit.screens.ramp.amount.RampAmountData
import com.tonapps.scanner.ScannerScreen
import com.tonapps.deposit.screens.send.SendExchangeData
import com.tonapps.deposit.screens.send.SendFeature
import com.tonapps.deposit.screens.send.SendFeatureData
import com.tonapps.deposit.screens.send.SendModel
import com.tonapps.deposit.screens.send.SendParams
import com.tonapps.deposit.screens.send.SendScreen
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import ui.moon.MoonNav

private const val KEY_TOKEN_SELECTION_RESULT = "withdraw_token_selection_result"
private const val KEY_SCANNER_RESULT = "withdraw_scanner_result"

internal fun WalletCurrency.toSellAsset(): WithdrawFlowSellAsset {
    return when (address) {
        WalletCurrency.USDT_TON.address -> WithdrawFlowSellAsset.TonJettonUSDT
        WalletCurrency.USDT_TRON.address -> WithdrawFlowSellAsset.TronTrc20USDT
        else -> WithdrawFlowSellAsset.TonNativeTON
    }
}

@Serializable
sealed interface WithdrawRoutes : NavKey {

    @Serializable
    data object Ramp : WithdrawRoutes

    @Serializable
    data class SendAmount(
        val currency: WalletCurrency? = null,
        val address: String? = null,
        val exchangeData: SendExchangeData? = null,
        val from: Events.SendNative.SendNativeFrom? = null,
    ) : WithdrawRoutes

    @Serializable
    data object SendConfirm : WithdrawRoutes

    @Serializable
    data class ExtendedCryptoList(val filter: AssetFilter = AssetFilter.All, val preferredCurrency: String? = null) : WithdrawRoutes

    @Serializable
    data class WithdrawMethod(
        val asset: RampAsset? = null,
        val ft: String? = null,
        val tn: String? = null,
        val tt: String? = null,
        val fn: String? = null,
        val cm: String? = null,
        val sectionFilter: PaymentMethodSectionFilter = PaymentMethodSectionFilter.All,
        val preferredCurrency: String? = null,
    ) : WithdrawRoutes

    @Serializable
    data class SelectNetwork(val to: RampAsset, val stablecoinCode: String, val selectedSymbol: String? = null, val fiatCurrency: WalletCurrency? = null) : WithdrawRoutes

    @Serializable
    data class SelectCurrency(val selectedCode: String? = null) : WithdrawRoutes

    @Serializable
    data class TokenPicker(val selectedTokenAddress: String? = null) : WithdrawRoutes

    @Serializable
    data object Scanner : WithdrawRoutes

    @Serializable
    data class Amount(
        val asset: RampAsset,
        val paymentMethodType: String,
        val fiatCurrency: WalletCurrency
    ) : WithdrawRoutes

}

@Composable
fun WithdrawRouter(
    initial: WithdrawRoutes,
    onBack: () -> Unit,
    onSendSuccess: () -> Unit = onBack,
    openProvider: (String) -> Unit,
    onShowError: (String) -> Unit,
    onAddressBook: ((onResult: (String) -> Unit) -> Unit)? = null,
    onBuyTon: () -> Unit = {},
    onGetTrx: () -> Unit = {},
    onRechargeBattery: () -> Unit = {},
) {
    val backStack = rememberNavBackStack(initial)
    val resultStore = rememberResultStore()
    var sendParams by remember { mutableStateOf<SendParams?>(null) } // TODO remove
    val popBackStack = {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        } else {
            onBack()
        }
    }
    
    CompositionLocalProvider(LocalResultStore provides resultStore) {
        MoonNav(
            backStack = backStack,
        ) { key ->
            when (key) {
                is WithdrawRoutes.Ramp -> NavEntry(key) {
                    val viewModel = koinViewModel<RampFeature> { parametersOf(RampType.RampOff) }
                    RampScreen(
                        feature = viewModel,
                        rampType = RampType.RampOff,
                        onClose = onBack,
                        onQr = { },
                        onSend = {
                            AnalyticsHelper.Default.events.withdrawFlow.withdrawClickSendTokens(from = WithdrawFlowFrom.WalletScreen)
                            backStack.add(WithdrawRoutes.SendAmount(null))
                        },
                        onBuyCash = { preferredCurrency ->
                            backStack.add(WithdrawRoutes.ExtendedCryptoList(filter = AssetFilter.Cash, preferredCurrency = preferredCurrency))
                        },
                        onBuyCrypto = { _ -> },
                        onBuyStablecoins = {
                            backStack.add(WithdrawRoutes.ExtendedCryptoList(filter = AssetFilter.StablecoinRoot))
                        },
                    )
                }

                is WithdrawRoutes.SendAmount -> NavEntry(key) {
                    val feature = koinViewModel<SendFeature> {
                        parametersOf(SendFeatureData(key.currency, key.address, key.exchangeData))
                    }

                    LaunchedEffect(Unit) {
                        val result = resultStore.removeResult<TokenEntity>(KEY_TOKEN_SELECTION_RESULT)
                        if (result != null) {
                            feature.sendAction(SendModel.Action.SelectToken(result))
                        }
                    }

                    SendScreen(
                        feature = feature,
                        scannerResult = resultStore.removeResult<String>(KEY_SCANNER_RESULT),
                        onClose = onBack,
                        onBack = { popBackStack() },
                        onAddressBook = onAddressBook,
                        onNavigateToConfirm = { params ->
                            sendParams = params
                            backStack.add(WithdrawRoutes.SendConfirm)
                        },
                        onNavigateToTokenPicker = { selectedAddress ->
                            backStack.add(WithdrawRoutes.TokenPicker(selectedTokenAddress = selectedAddress))
                        },
                        onNavigateToScanner = {
                            backStack.add(WithdrawRoutes.Scanner)
                        },
                        onShowError = onShowError
                    )
                }

                is WithdrawRoutes.SendConfirm -> NavEntry(key) {
                    val params = sendParams ?: run {
                        popBackStack()
                        return@NavEntry
                    }

                    val feature = koinViewModel<ConfirmFeature> { parametersOf(params) }

                    SendConfirmScreen(
                        feature = feature,
                        onClose = onBack,
                        onSendSuccess = onSendSuccess,
                        onBack = { popBackStack() },
                        onBuyTon = onBuyTon,
                        onGetTrx = onGetTrx,
                        onRechargeBattery = onRechargeBattery,
                    )
                }

                is WithdrawRoutes.ExtendedCryptoList -> NavEntry(key) {
                    val viewModel = koinViewModel<AssetsCryptoExtendedFeature> { parametersOf(AssetsCryptoExtendedFeatureData(RampType.RampOff, key.filter)) }

                    val sectionFilter = when (key.filter) {
                        AssetFilter.Cash -> PaymentMethodSectionFilter.CashOnly
                        AssetFilter.Stablecoin, AssetFilter.StablecoinRoot -> PaymentMethodSectionFilter.StablecoinOnly
                        AssetFilter.All -> PaymentMethodSectionFilter.All
                    }

                    AssetsExtendedScreen(
                        feature = viewModel,
                        title = "Asset to withdraw",
                        onClose = onBack,
                        onBack = { popBackStack() },
                        onSelected = { currency ->
                            when (key.filter) {
                                AssetFilter.StablecoinRoot -> {
                                    AnalyticsHelper.Default.events.withdrawFlow.withdrawClickSell(
                                        from = WithdrawFlowFrom.WalletScreen,
                                        sellAsset = currency.toSellAsset()
                                    )

                                    backStack.add(WithdrawRoutes.WithdrawMethod(
                                        asset = RampAsset.Currency(currency),
                                        sectionFilter = PaymentMethodSectionFilter.StablecoinOnly,
                                    ))
                                }
                                AssetFilter.Stablecoin -> {
                                    val rootAsset = RampAsset.Currency(currency)
                                    backStack.add(WithdrawRoutes.SelectNetwork(
                                        to = rootAsset,
                                        stablecoinCode = rootAsset.currencyCode,
                                        selectedSymbol = currency.code,
                                    ))
                                }
                                else -> {
                                    AnalyticsHelper.Default.events.withdrawFlow.withdrawClickSell(
                                        from = WithdrawFlowFrom.WalletScreen,
                                        sellAsset = currency.toSellAsset()
                                    )

                                    backStack.add(WithdrawRoutes.WithdrawMethod(
                                        asset = RampAsset.Currency(currency),
                                        sectionFilter = sectionFilter,
                                        preferredCurrency = key.preferredCurrency,
                                    ))
                                }
                            }
                        },
                    )
                }

                is WithdrawRoutes.WithdrawMethod -> NavEntry(key) {
                    val viewModel = koinViewModel<PaymentMethodFeature> {
                        parametersOf(PaymentMethodFeatureData(key.asset, RampType.RampOff, key.ft, key.tn, key.tt, key.fn, key.cm, key.sectionFilter, key.preferredCurrency))
                    }
                    val currencyResult = resultStore.removeResult<WalletCurrency>(KEY_CURRENCY_SELECTION_RESULT)

                    PaymentMethodScreen(
                        feature = viewModel,
                        fallbackAsset = key.asset,
                        currencySelectionResult = currencyResult,
                        onClose = onBack,
                        onBack = { popBackStack() },
                        onPaymentMethodClick = { asset, paymentMethodType, currency ->
                            backStack.add(
                                WithdrawRoutes.Amount(
                                    asset = asset,
                                    paymentMethodType = paymentMethodType,
                                    fiatCurrency = currency
                                )
                            )
                        },
                        onBuyWithCrypto = { asset, destinationCurrency ->
                            val fromCurrency = (asset as? RampAsset.Currency)?.currency
                            backStack.add(WithdrawRoutes.SendAmount(
                                currency = fromCurrency,
                                exchangeData = SendExchangeData(exchangeTo = destinationCurrency),
                            ))
                        },
                        onSelectCurrency = { selectedCode ->
                            backStack.add(WithdrawRoutes.SelectCurrency(selectedCode = selectedCode))
                        },
                        onSelectNetwork = { asset, selectedSymbol, fiatCurrency ->
                            backStack.add(WithdrawRoutes.SelectNetwork(
                                to = asset,
                                stablecoinCode = asset.currencyCode,
                                selectedSymbol = selectedSymbol,
                                fiatCurrency = fiatCurrency,
                            ))
                        },
                        onOpenP2P = openProvider,
                        onAutoNavigateCrypto = { resolvedAsset, targetCurrency ->
                            val fromCurrency = (resolvedAsset as? RampAsset.Currency)?.currency
                            backStack.add(WithdrawRoutes.SendAmount(
                                currency = fromCurrency,
                                exchangeData = SendExchangeData(exchangeTo = targetCurrency),
                            ))
                        },
                        onAutoNavigateCash = { resolvedAsset, paymentMethodType, fiatCurrency ->
                            if (paymentMethodType == "p2p") { // TODO handle
                                return@PaymentMethodScreen
                            }

                            backStack.add(
                                WithdrawRoutes.Amount(
                                    asset = resolvedAsset,
                                    paymentMethodType = paymentMethodType,
                                    fiatCurrency = fiatCurrency,
                                )
                            )
                        },
                    )
                }

                is WithdrawRoutes.SelectNetwork -> NavEntry(key) {
                    val feature = koinViewModel<SelectNetworkFeature> {
                        parametersOf(SelectNetworkData(
                            stablecoinCode = key.stablecoinCode,
                            stablecoinNetwork = key.to.network,
                            rampType = RampType.RampOff,
                            selectedSymbol = key.selectedSymbol,
                        ))
                    }
                    val fromCurrency = (key.to as? RampAsset.Currency)?.currency
                    SelectNetworkScreen(
                        feature = feature,
                        showFee = true,
                        onSelect = { networkInfo ->
                            backStack.add(WithdrawRoutes.SendAmount(
                                currency = fromCurrency,
                                exchangeData = SendExchangeData(
                                    exchangeTo = networkInfo.currency,
                                    withdrawalFee = networkInfo.fee,
                                    fiatCurrency = key.fiatCurrency,
                                    minAmount = networkInfo.providerMinAmount,
                                    maxAmount = networkInfo.providerMaxAmount,
                                ),
                            ))
                        },
                        onClose = onBack,
                        onBack = { popBackStack() },
                    )
                }

                is WithdrawRoutes.SelectCurrency -> NavEntry(key) {
                    val feature = koinViewModel<SelectCurrencyFeature>()
                    SelectCurrencyScreen(
                        feature = feature,
                        selectedCurrencyCode = key.selectedCode,
                        onConfirm = { currency ->
                            resultStore.setResult(KEY_CURRENCY_SELECTION_RESULT, currency)
                        },
                        onBack = { popBackStack() },
                        onClose = onBack,
                    )
                }

                is WithdrawRoutes.TokenPicker -> NavEntry(key) {
                    val feature = koinViewModel<TokenPickerFeature>()
                    TokenPickerScreen(
                        feature = feature,
                        selectedTokenAddress = key.selectedTokenAddress,
                        onTokenSelected = { token ->
                            resultStore.setResult(KEY_TOKEN_SELECTION_RESULT, token.token)
                        },
                        onBack = { popBackStack() },
                        onClose = onBack,
                    )
                }

                is WithdrawRoutes.Scanner -> NavEntry(key) {
                    ScannerScreen(
                        onResult = { value ->
                            resultStore.setResult(KEY_SCANNER_RESULT, value)
                            backStack.removeLastOrNull()
                        },
                        onClose = { backStack.removeLastOrNull() },
                    )
                }

                is WithdrawRoutes.Amount -> NavEntry(key) {
                    val amountData = RampAmountData(
                        assetFrom = key.asset,
                        assetTo = RampAsset.Currency(key.fiatCurrency),
                        paymentMethodType = key.paymentMethodType,
                    )
                    val viewModel = koinViewModel<DepositAmountFeature> {
                        parametersOf(amountData)
                    }

                    DepositAmountScreen(
                        feature = viewModel,
                        onClose = onBack,
                        onBack = { popBackStack() },
                        onContinue = openProvider,
                    )
                }

                else -> throw IllegalStateException("Unknown key: $key")
            }
        }
    }
}
