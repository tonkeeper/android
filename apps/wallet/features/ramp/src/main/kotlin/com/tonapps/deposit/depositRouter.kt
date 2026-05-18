package com.tonapps.deposit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowBuyAsset
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.core.navigation.LocalResultStore
import com.tonapps.core.navigation.rememberResultStore
import com.tonapps.deposit.data.AssetFilter
import com.tonapps.deposit.screens.assets.AssetsCryptoExtendedFeature
import com.tonapps.deposit.screens.assets.AssetsCryptoExtendedFeatureData
import com.tonapps.deposit.screens.assets.AssetsExtendedScreen
import com.tonapps.deposit.screens.buy.crypto.BuyWithCryptoFeature
import com.tonapps.deposit.screens.buy.crypto.BuyWithCryptoScreen
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
import com.tonapps.deposit.screens.qr.QrAssetData
import com.tonapps.deposit.screens.qr.QrAssetFeature
import com.tonapps.deposit.screens.qr.QrScreen
import com.tonapps.deposit.screens.ramp.RampFeature
import com.tonapps.deposit.screens.ramp.RampScreen
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.deposit.screens.ramp.amount.DepositAmountFeature
import com.tonapps.deposit.screens.ramp.amount.DepositAmountScreen
import com.tonapps.deposit.screens.ramp.amount.RampAmountData
import com.tonapps.wallet.localization.Localization
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import ui.moon.MoonNav

internal fun WalletCurrency.toBuyAsset(): DepositFlowBuyAsset {
    return when (address) {
        WalletCurrency.USDT_TON.address -> DepositFlowBuyAsset.TonJettonUSDT
        WalletCurrency.USDT_TRON.address -> DepositFlowBuyAsset.TronTrc20USDT
        else -> DepositFlowBuyAsset.TonNativeTON
    }
}

@Serializable
sealed interface DepositRoutes : NavKey {
    @Serializable
    data object Ramp : DepositRoutes

    @Serializable
    data class ExtendedCryptoList(val filter: AssetFilter = AssetFilter.All, val preferredCurrency: String? = null) : DepositRoutes

    @Serializable
    data class Buy(
        val asset: RampAsset? = null,
        val ft: String? = null,
        val tn: String? = null,
        val tt: String? = null,
        val fn: String? = null,
        val cm: String? = null,
        val sectionFilter: PaymentMethodSectionFilter = PaymentMethodSectionFilter.All,
        val preferredCurrency: String? = null,
    ) : DepositRoutes

    @Serializable
    data class SelectNetwork(val to: RampAsset, val stablecoinCode: String, val selectedSymbol: String? = null) : DepositRoutes

    @Serializable
    data object Qr : DepositRoutes

    @Serializable
    data class BuyWithCrypto(
        val from: WalletCurrency,
        val to: RampAsset,
    ) : DepositRoutes

    @Serializable
    data class SelectCurrency(val selectedCode: String? = null) : DepositRoutes

    @Serializable
    data class Amount(
        val asset: RampAsset,
        val paymentMethodType: String,
        val fiatCurrency: WalletCurrency
    ) : DepositRoutes

}

@Composable
fun DepositRouter(
    initial: DepositRoutes = DepositRoutes.Ramp,
    onBack: () -> Unit,
    openProvider: (String) -> Unit,
) {
    val backStack = rememberNavBackStack(initial)
    val resultStore = rememberResultStore()
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
                is DepositRoutes.Ramp -> NavEntry(key) {
                    val viewModel = koinViewModel<RampFeature> { parametersOf(RampType.RampOn) }
                    RampScreen(
                        feature = viewModel,
                        rampType = RampType.RampOn,
                        onClose = onBack,
                        onQr = {
                            AnalyticsHelper.Default.events.depositFlow.depositClickReceiveTokens(from = DepositFlowFrom.WalletScreen)
                            backStack.add(DepositRoutes.Qr)
                        },
                        onSend = { },
                        onBuyCash = { preferredCurrency ->
                            backStack.add(DepositRoutes.ExtendedCryptoList(filter = AssetFilter.Cash, preferredCurrency = preferredCurrency))
                        },
                        onBuyCrypto = { asset ->
                            backStack.add(DepositRoutes.Buy(
                                asset = asset,
                                sectionFilter = PaymentMethodSectionFilter.CryptoOnly,
                            ))
                        },
                        onBuyStablecoins = {
                            backStack.add(DepositRoutes.ExtendedCryptoList(filter = AssetFilter.StablecoinRoot))
                        },
                    )
                }

                is DepositRoutes.ExtendedCryptoList -> NavEntry(key) {
                    val viewModel = koinViewModel<AssetsCryptoExtendedFeature> { parametersOf(AssetsCryptoExtendedFeatureData(RampType.RampOn, key.filter)) }

                    val sectionFilter = when (key.filter) {
                        AssetFilter.Cash -> PaymentMethodSectionFilter.CashOnly
                        AssetFilter.Stablecoin, AssetFilter.StablecoinRoot -> PaymentMethodSectionFilter.StablecoinOnly
                        AssetFilter.All -> PaymentMethodSectionFilter.All
                    }

                    AssetsExtendedScreen(
                        feature = viewModel,
                        title = stringResource(Localization.choose_asset),
                        onClose = onBack,
                        onBack = { popBackStack() },
                        onSelected = { currency ->
                            when (key.filter) {
                                AssetFilter.StablecoinRoot -> {
                                    AnalyticsHelper.Default.events.depositFlow.depositClickBuy(
                                        buyAsset = currency.toBuyAsset()
                                    )
                                    backStack.add(DepositRoutes.Buy(
                                        asset = RampAsset.Currency(currency),
                                        sectionFilter = PaymentMethodSectionFilter.StablecoinOnly,
                                    ))
                                }
                                AssetFilter.Stablecoin -> {
                                    val rootAsset = RampAsset.Currency(currency)
                                    backStack.add(DepositRoutes.SelectNetwork(
                                        to = rootAsset,
                                        stablecoinCode = rootAsset.currencyCode,
                                        selectedSymbol = currency.code,
                                    ))
                                }
                                else -> {
                                    AnalyticsHelper.Default.events.depositFlow.depositClickBuy(
                                        buyAsset = currency.toBuyAsset()
                                    )
                                    backStack.add(DepositRoutes.Buy(
                                        asset = RampAsset.Currency(currency),
                                        sectionFilter = sectionFilter,
                                        preferredCurrency = key.preferredCurrency,
                                    ))
                                }
                            }
                        },
                    )
                }

                is DepositRoutes.Buy -> NavEntry(key) {
                    val viewModel = koinViewModel<PaymentMethodFeature> {
                        parametersOf(PaymentMethodFeatureData(key.asset, RampType.RampOn, key.ft, key.tn, key.tt, key.fn, key.cm, key.sectionFilter, key.preferredCurrency))
                    }
                    val currencyResult = resultStore.removeResult<WalletCurrency>(KEY_CURRENCY_SELECTION_RESULT)

                    PaymentMethodScreen(
                        feature = viewModel,
                        fallbackAsset = key.asset,
                        currencySelectionResult = currencyResult,
                        onClose = onBack,
                        onBack = { popBackStack() },
                        onPaymentMethodClick = { asset, paymentMethodType, fiatCurrency ->
                            backStack.add(
                                DepositRoutes.Amount(
                                    asset = asset,
                                    paymentMethodType = paymentMethodType,
                                    fiatCurrency = fiatCurrency
                                )
                            )
                        },
                        onBuyWithCrypto = { asset, currency ->
                            backStack.add(DepositRoutes.BuyWithCrypto(from = currency, to = asset))
                        },
                        onSelectCurrency = { selectedCode ->
                            backStack.add(DepositRoutes.SelectCurrency(selectedCode = selectedCode))
                        },
                        onSelectNetwork = { asset, selectedSymbol, _ ->
                            backStack.add(DepositRoutes.SelectNetwork(
                                to = asset,
                                stablecoinCode = asset.currencyCode,
                                selectedSymbol = selectedSymbol,
                            ))
                        },
                        onOpenP2P = openProvider,
                        onAutoNavigateCrypto = { resolvedAsset, targetCurrency ->
                            backStack.add(DepositRoutes.BuyWithCrypto(from = targetCurrency, to = resolvedAsset))
                        },
                        onAutoNavigateCash = { resolvedAsset, paymentMethodType, fiatCurrency ->
                            if (paymentMethodType == "p2p") { // TODO handle
                                return@PaymentMethodScreen
                            }

                            backStack.add(
                                DepositRoutes.Amount(
                                    asset = resolvedAsset,
                                    paymentMethodType = paymentMethodType,
                                    fiatCurrency = fiatCurrency,
                                )
                            )
                        },
                    )
                }

                is DepositRoutes.SelectNetwork -> NavEntry(key) {
                    val feature = koinViewModel<SelectNetworkFeature> {
                        parametersOf(SelectNetworkData(
                            stablecoinCode = key.stablecoinCode,
                            stablecoinNetwork = key.to.network,
                            rampType = RampType.RampOn,
                            selectedSymbol = key.selectedSymbol,
                        ))
                    }
                    SelectNetworkScreen(
                        feature = feature,
                        onSelect = { networkInfo ->
                            backStack.add(DepositRoutes.BuyWithCrypto(from = networkInfo.currency, to = key.to))
                        },
                        onClose = onBack,
                        onBack = { popBackStack() },
                    )
                }

                is DepositRoutes.Qr -> NavEntry(key) {
                    val viewModel = koinViewModel<QrAssetFeature> {
                        parametersOf(QrAssetData())
                    }

                    QrScreen(
                        viewModel = viewModel,
                        showBuyButton = false, // TODO
                        onFinishClick = { popBackStack() },
                        onBuyClick = {}, // TODO
                    )
                }

                is DepositRoutes.BuyWithCrypto -> NavEntry(key) {
                    val viewModel = koinViewModel<BuyWithCryptoFeature> { parametersOf(key.from, key.to) }
                    BuyWithCryptoScreen(
                        viewModel = viewModel,
                        from = key.from,
                        to = key.to,
                        onClose = onBack,
                        onBack = { popBackStack() },
                    )
                }

                is DepositRoutes.SelectCurrency -> NavEntry(key) {
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

                is DepositRoutes.Amount -> NavEntry(key) {
                    val amountData = RampAmountData(
                        assetFrom = RampAsset.Currency(key.fiatCurrency),
                        assetTo = key.asset,
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
