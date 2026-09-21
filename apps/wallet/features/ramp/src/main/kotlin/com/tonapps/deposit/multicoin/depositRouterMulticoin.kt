package com.tonapps.deposit.multicoin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowAddFundsOption
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.core.navigation.LocalResultStore
import com.tonapps.core.navigation.rememberResultStore
import com.tonapps.deposit.multicoin.screens.assets.AssetsExtendedFeature
import com.tonapps.deposit.multicoin.screens.assets.AssetsExtendedFeatureData
import com.tonapps.deposit.multicoin.screens.assets.AssetsExtendedScreen
import com.tonapps.deposit.multicoin.screens.method.PaymentMethodFeature
import com.tonapps.deposit.multicoin.screens.method.PaymentMethodFeatureData
import com.tonapps.deposit.multicoin.screens.method.PaymentMethodScreen
import com.tonapps.deposit.multicoin.screens.ramp.RampFeature
import com.tonapps.deposit.multicoin.screens.ramp.RampScreen
import com.tonapps.deposit.multicoin.screens.ramp.amount.RampAmountData
import com.tonapps.deposit.multicoin.screens.ramp.amount.RampAmountFeature
import com.tonapps.deposit.multicoin.screens.ramp.amount.RampAmountScreen
import com.tonapps.deposit.multicoin.screens.receive.ReceiveFeature
import com.tonapps.deposit.multicoin.screens.receive.ReceiveScreen
import com.tonapps.deposit.screens.currency.SelectCurrencyFeature
import com.tonapps.deposit.screens.currency.SelectCurrencyScreen
import com.tonapps.deposit.screens.method.KEY_CURRENCY_SELECTION_RESULT
import com.tonapps.deposit.screens.ramp.RampType
import kotlinx.serialization.Serializable
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import ui.moon.MoonNav
import ui.moon.rememberNestedNavBackStack

@Serializable
sealed interface DepositMulticoinRoutes : NavKey {

    @Serializable
    data object Ramp : DepositMulticoinRoutes

    @Serializable
    data object Receive : DepositMulticoinRoutes

    @Serializable
    data class ExtendedCryptoList(
        val preferredCurrency: String? = null,
    ) : DepositMulticoinRoutes

    @Serializable
    data class Buy(
        val assetId: String,
        val preferredCurrency: String? = null,
    ) : DepositMulticoinRoutes

    @Serializable
    data class SelectCurrency(
        val selectedCode: String? = null,
        val availableCodes: List<String>? = null,
    ) : DepositMulticoinRoutes

    @Serializable
    data class Amount(
        val assetId: String,
        val paymentMethodType: String,
        val fiat: String? = null,
    ) : DepositMulticoinRoutes
}

@Composable
fun DepositMulticoinRouter(
    initial: DepositMulticoinRoutes?,
    onBack: () -> Unit,
    rampType: RampType = RampType.RampOn,
    analyticsFrom: DepositFlowFrom = DepositFlowFrom.WalletScreen,
    onOpenWidget: (String) -> Unit = {},
) {
    val backStack = rememberNestedNavBackStack(initial ?: DepositMulticoinRoutes.Ramp, onBack)
    val resultStore = rememberResultStore()

    CompositionLocalProvider(LocalResultStore provides resultStore) {
        MoonNav(
            backStack = backStack,
        ) { key ->
            when (key) {
                is DepositMulticoinRoutes.Ramp -> NavEntry(key) {
                    val feature = koinViewModel<RampFeature> {
                        parametersOf(rampType, analyticsFrom)
                    }
                    RampScreen(
                        feature = feature,
                        rampType = rampType,
                        onClose = onBack,
                        onReceive = {
                            feature.analytics.optionClick(DepositFlowAddFundsOption.ReceiveTokens)
                            backStack.add(DepositMulticoinRoutes.Receive)
                        },
                        onSend = { },
                        onCardClick = { card ->
                            feature.analytics.optionClick(DepositFlowAddFundsOption.BuyWithFiat)
                            backStack.add(
                                DepositMulticoinRoutes.ExtendedCryptoList(
                                    preferredCurrency = card.preferredCurrency,
                                )
                            )
                        },
                    )
                }

                is DepositMulticoinRoutes.Receive -> NavEntry(key) {
                    val feature = koinViewModel<ReceiveFeature> {
                        parametersOf(rampType, analyticsFrom)
                    }
                    ReceiveScreen(
                        feature = feature,
                        onClose = onBack,
                        onBack = if (backStack.size == 1) {
                            null
                        } else {
                            { backStack.safeRemoveLastOrNull(key) }
                        },
                    )
                }

                is DepositMulticoinRoutes.ExtendedCryptoList -> NavEntry(key) {
                    val feature = koinViewModel<AssetsExtendedFeature> {
                        parametersOf(
                            AssetsExtendedFeatureData(
                                rampType = rampType,
                                analyticsFrom = analyticsFrom,
                                preferredCurrency = key.preferredCurrency,
                            )
                        )
                    }
                    AssetsExtendedScreen(
                        feature = feature,
                        onClose = onBack,
                        onBack = { backStack.safeRemoveLastOrNull(key) },
                        onSelected = { asset ->
                            backStack.add(
                                DepositMulticoinRoutes.Buy(
                                    assetId = asset.asset.id,
                                    preferredCurrency = key.preferredCurrency,
                                )
                            )
                        },
                    )
                }

                is DepositMulticoinRoutes.Buy -> NavEntry(key) {
                    val feature = koinViewModel<PaymentMethodFeature> {
                        parametersOf(
                            PaymentMethodFeatureData(
                                rampType = rampType,
                                assetId = key.assetId,
                                analyticsFrom = analyticsFrom,
                                preferredCurrency = key.preferredCurrency,
                            )
                        )
                    }
                    val currencyResult =
                        resultStore.removeResult<WalletCurrency>(KEY_CURRENCY_SELECTION_RESULT)

                    PaymentMethodScreen(
                        feature = feature,
                        currencySelectionResult = currencyResult,
                        onClose = onBack,
                        onBack = { backStack.safeRemoveLastOrNull(key) },
                        onSelectCurrency = { selectedCode, availableCodes ->
                            backStack.add(
                                DepositMulticoinRoutes.SelectCurrency(
                                    selectedCode = selectedCode,
                                    availableCodes = availableCodes,
                                )
                            )
                        },
                        onPaymentMethodSelected = { asset, method, fiatCode ->
                            backStack.add(
                                DepositMulticoinRoutes.Amount(
                                    assetId = asset.assetId,
                                    paymentMethodType = method.type.value,
                                    fiat = fiatCode,
                                )
                            )
                        },
                        onOpenP2P = { url -> onOpenWidget(url) },
                    )
                }

                is DepositMulticoinRoutes.SelectCurrency -> NavEntry(key) {
                    val feature = koinViewModel<SelectCurrencyFeature>()
                    SelectCurrencyScreen(
                        feature = feature,
                        selectedCurrencyCode = key.selectedCode,
                        availableCodes = key.availableCodes,
                        onConfirm = { currency ->
                            resultStore.setResult(KEY_CURRENCY_SELECTION_RESULT, currency)
                        },
                        onBack = { backStack.safeRemoveLastOrNull(key) },
                        onClose = onBack,
                    )
                }

                is DepositMulticoinRoutes.Amount -> NavEntry(key) {
                    val feature = koinViewModel<RampAmountFeature> {
                        parametersOf(
                            RampAmountData(
                                rampType = rampType,
                                assetId = key.assetId,
                                paymentMethodType = key.paymentMethodType,
                                analyticsFrom = analyticsFrom,
                                fiat = key.fiat,
                            )
                        )
                    }
                    RampAmountScreen(
                        feature = feature,
                        onClose = onBack,
                        onBack = { backStack.safeRemoveLastOrNull(key) },
                        onContinue = { url ->
                            onOpenWidget(url)
                        },
                    )
                }

                else -> throw IllegalStateException("Unknown key: $key")
            }
        }
    }
}
