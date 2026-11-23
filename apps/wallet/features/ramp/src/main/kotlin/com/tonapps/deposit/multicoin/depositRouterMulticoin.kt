package com.tonapps.deposit.multicoin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.Fragment
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.SAVED_STATE_REGISTRY_OWNER_KEY
import androidx.lifecycle.VIEW_MODEL_STORE_OWNER_KEY
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.ViewModelStoreProvider
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreProvider
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import java.util.UUID
import androidx.navigation3.runtime.NavKey
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowAddFundsOption
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowDepositScreen
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.core.flags.WalletFeature
import com.tonapps.core.navigation.LocalResultStore
import com.tonapps.core.navigation.ResultStore
import com.tonapps.core.navigation.rememberResultStore
import com.tonapps.deposit.multicoin.screens.assets.AssetsExtendedFeature
import com.tonapps.deposit.multicoin.moonpay.frame.MoonPayTheme
import com.tonapps.deposit.multicoin.screens.moonpay.KEY_MOONPAY_PAYMENT_RESULT
import com.tonapps.deposit.multicoin.screens.moonpay.MoonPayPaymentData
import com.tonapps.deposit.multicoin.screens.assets.AssetsExtendedFeatureData
import com.tonapps.deposit.multicoin.screens.assets.AssetsExtendedScreen
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoAction
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoData
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoFeature
import com.tonapps.deposit.multicoin.screens.buy.BuyCryptoScreen
import com.tonapps.deposit.multicoin.screens.buy.KEY_BUY_CRYPTO_ASSET_RESULT
import com.tonapps.deposit.multicoin.screens.buy.method.BuyCryptoMethodData
import com.tonapps.deposit.multicoin.screens.buy.method.BuyCryptoMethodFeature
import com.tonapps.deposit.multicoin.screens.buy.method.BuyCryptoMethodScreen
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
import ui.theme.UIKit
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
    data object BuyCryptoReceive : DepositMulticoinRoutes

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
    data class BuyCrypto(
        val assetId: String? = null,
        val preferredCurrency: String? = null,
        // One opening of the screen: its view model, shared with the method screen, lives as long as this route.
        val open: String = UUID.randomUUID().toString(),
    ) : DepositMulticoinRoutes

    @Serializable
    data class BuyCryptoAsset(val fiat: String) : DepositMulticoinRoutes

    @Serializable
    data class BuyCryptoMethod(val buy: String) : DepositMulticoinRoutes

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
    // Keeps the state of «Buy crypto», shared by its screens: it saves it as long as the flow lives.
    flowOwner: Fragment,
    walletId: String,
    initial: DepositMulticoinRoutes?,
    onBack: () -> Unit,
    rampType: RampType = RampType.RampOn,
    analyticsFrom: DepositFlowFrom,
    utm: UtmParameters = UtmParameters.Empty,
    onOpenWidget: (String) -> Unit = {},
    onOpenMoonPay: (MoonPayPaymentData) -> Unit,
    paymentEnded: BuyCryptoAction.PaymentEnded?,
    onPaymentEndedTaken: () -> Unit,
) {
    val buyCrypto = remember { buyCryptoEnabled(rampType, WalletFeature.MoonPayRamp.isEnabled) }
    val backStack = rememberNestedNavBackStack(startRoute(initial, buyCrypto), onBack)
    val buyCryptoStores = rememberViewModelStoreProvider(key = KEY_BUY_CRYPTO_STORES)
    val entryDecorators = listOf(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
        remember(buyCryptoStores) { buyCryptoStoreDecorator(buyCryptoStores) },
    )
    val resultStore = rememberResultStore()
    // Outlives «Buy crypto» leaving the composition under a picker, not a recreation: a new screen opens with the keyboard.
    var buyCryptoKeyboard by remember { mutableStateOf(true) }
    val moonPayTheme = if (UIKit.isLightTheme) {
        MoonPayTheme.Light
    } else {
        MoonPayTheme.Dark
    }

    LaunchedEffect(paymentEnded) {
        if (paymentEnded != null) {
            resultStore.setResult(KEY_MOONPAY_PAYMENT_RESULT, paymentEnded)
            onPaymentEndedTaken()
        }
    }

    CompositionLocalProvider(LocalResultStore provides resultStore) {
        MoonNav(
            backStack = backStack,
            entryDecorators = entryDecorators,
        ) { key ->
            when (key) {
                is DepositMulticoinRoutes.Ramp -> NavEntry(key) {
                    val feature = koinViewModel<RampFeature> {
                        parametersOf(walletId, rampType, analyticsFrom, utm)
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
                            buyCryptoKeyboard = true
                            backStack.add(
                                if (buyCrypto) {
                                    DepositMulticoinRoutes.BuyCrypto(preferredCurrency = card.preferredCurrency)
                                } else {
                                    DepositMulticoinRoutes.ExtendedCryptoList(preferredCurrency = card.preferredCurrency)
                                }
                            )
                        },
                    )
                }

                is DepositMulticoinRoutes.Receive, is DepositMulticoinRoutes.BuyCryptoReceive -> NavEntry(key) {
                    val screen = if (key is DepositMulticoinRoutes.BuyCryptoReceive) {
                        DepositFlowDepositScreen.BuyCrypto
                    } else {
                        DepositFlowDepositScreen.AddFunds
                    }
                    val feature = koinViewModel<ReceiveFeature> {
                        parametersOf(walletId, rampType, analyticsFrom, utm, screen)
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
                                walletId = walletId,
                                rampType = rampType,
                                analyticsFrom = analyticsFrom,
                                preferredCurrency = key.preferredCurrency,
                                utm = utm,
                                depositScreen = DepositFlowDepositScreen.AddFunds,
                            )
                        )
                    }
                    AssetsExtendedScreen(
                        feature = feature,
                        onClose = onBack,
                        onBack = { backStack.safeRemoveLastOrNull(key) },
                        onSelected = { asset ->
                            feature.onSelected(asset)
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
                                walletId = walletId,
                                rampType = rampType,
                                assetId = key.assetId,
                                analyticsFrom = analyticsFrom,
                                preferredCurrency = key.preferredCurrency,
                                utm = utm,
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
                    val feature = koinViewModel<SelectCurrencyFeature> { parametersOf(walletId) }
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
                                walletId = walletId,
                                rampType = rampType,
                                assetId = key.assetId,
                                paymentMethodType = key.paymentMethodType,
                                analyticsFrom = analyticsFrom,
                                fiat = key.fiat,
                                utm = utm,
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

                is DepositMulticoinRoutes.BuyCrypto -> NavEntry(key, contentKey = key.open) {
                    val feature = buyCryptoFeature(walletId, key, buyCryptoStores, flowOwner, moonPayTheme, analyticsFrom, utm)
                    ConsumeResult<WalletCurrency>(resultStore, KEY_CURRENCY_SELECTION_RESULT) {
                        feature.sendAction(BuyCryptoAction.SelectFiat(it.code))
                    }
                    ConsumeResult<BuyCryptoAction.SelectAsset>(resultStore, KEY_BUY_CRYPTO_ASSET_RESULT, feature::sendAction)
                    ConsumeResult<BuyCryptoAction.PaymentEnded>(resultStore, KEY_MOONPAY_PAYMENT_RESULT, feature::sendAction)
                    BuyCryptoScreen(
                        feature = feature,
                        onClose = onBack,
                        onOpenUrl = onOpenWidget,
                        onOpenMoonPay = onOpenMoonPay,
                        onPickCurrency = { fiat -> backStack.add(DepositMulticoinRoutes.SelectCurrency(selectedCode = fiat)) },
                        onPickAsset = { fiat -> backStack.add(DepositMulticoinRoutes.BuyCryptoAsset(fiat)) },
                        onPickMethod = { backStack.add(DepositMulticoinRoutes.BuyCryptoMethod(buy = key.open)) },
                        onCryptoDeposit = { backStack.add(DepositMulticoinRoutes.BuyCryptoReceive) },
                        showKeyboard = buyCryptoKeyboard,
                        onShowKeyboardChange = { buyCryptoKeyboard = it },
                        covered = backStack.lastOrNull() != key,
                    )
                }

                is DepositMulticoinRoutes.BuyCryptoAsset -> NavEntry(key) {
                    val feature = koinViewModel<AssetsExtendedFeature> {
                        parametersOf(
                            AssetsExtendedFeatureData(
                                walletId = walletId,
                                rampType = rampType,
                                analyticsFrom = analyticsFrom,
                                preferredCurrency = key.fiat,
                                utm = utm,
                                depositScreen = DepositFlowDepositScreen.BuyCrypto,
                            )
                        )
                    }
                    AssetsExtendedScreen(
                        feature = feature,
                        onClose = onBack,
                        onBack = { backStack.safeRemoveLastOrNull(key) },
                        onSelected = { asset ->
                            feature.onSelected(asset)
                            resultStore.setResult(
                                KEY_BUY_CRYPTO_ASSET_RESULT,
                                BuyCryptoAction.SelectAsset(
                                    assetId = asset.asset.id,
                                    fiat = key.fiat,
                                    symbol = asset.asset.symbol,
                                    networkName = asset.networkName,
                                    iconUrl = asset.asset.imageUrl,
                                ),
                            )
                            backStack.safeRemoveLastOrNull(key)
                        },
                    )
                }

                is DepositMulticoinRoutes.BuyCryptoMethod -> NavEntry(key) {
                    // Gone while this screen still animates out after a double back: nothing left to show.
                    val buyKey = backStack.filterIsInstance<DepositMulticoinRoutes.BuyCrypto>().lastOrNull { it.open == key.buy }
                        ?: return@NavEntry
                    val buy = buyCryptoFeature(walletId, buyKey, buyCryptoStores, flowOwner, moonPayTheme, analyticsFrom, utm)
                    val feature = koinViewModel<BuyCryptoMethodFeature> {
                        parametersOf(BuyCryptoMethodData(walletId, analyticsFrom, utm), moonPayTheme, buy)
                    }
                    BuyCryptoMethodScreen(
                        feature = feature,
                        onBack = { backStack.safeRemoveLastOrNull(key) },
                        onClose = onBack,
                        onPicked = { backStack.safeRemoveLastOrNull(key) },
                        onOpenUrl = onOpenWidget,
                        onReceive = { backStack.add(DepositMulticoinRoutes.BuyCryptoReceive) },
                    )
                }

                else -> throw IllegalStateException("Unknown key: $key")
            }
        }
    }
}

private const val KEY_BUY_CRYPTO_STORES = "buy_crypto_stores"

// A popped opening of «Buy crypto» clears its store once nothing on screen holds it; other entries are not in it.
private fun buyCryptoStoreDecorator(stores: ViewModelStoreProvider): NavEntryDecorator<NavKey> {
    return NavEntryDecorator(onPop = { contentKey -> stores.clearKey(contentKey) }) { entry -> entry.Content() }
}

// Saved in the fragment's state, not the entry's: the method screen changes it while «Buy crypto» is off screen.
@Composable
private fun buyCryptoFeature(
    walletId: String,
    key: DepositMulticoinRoutes.BuyCrypto,
    stores: ViewModelStoreProvider,
    flowOwner: Fragment,
    theme: MoonPayTheme,
    analyticsFrom: DepositFlowFrom,
    utm: UtmParameters,
): BuyCryptoFeature {
    val owner = rememberViewModelStoreOwner(key.open, stores, savedStateRegistryOwner = null)
    // The flow's owner saves the state: a store of its own has no saved state, and the entry's ends with the entry.
    val extras = MutableCreationExtras((owner as HasDefaultViewModelProviderFactory).defaultViewModelCreationExtras).apply {
        set(SAVED_STATE_REGISTRY_OWNER_KEY, flowOwner)
        set(VIEW_MODEL_STORE_OWNER_KEY, flowOwner)
    }
    return koinViewModel(viewModelStoreOwner = owner, key = "buy_crypto:${key.open}", extras = extras) {
        parametersOf(BuyCryptoData(walletId, key.assetId, key.preferredCurrency, analyticsFrom, utm), theme)
    }
}

// Read in composition, removed in the effect: the composition only observes the store.
@Composable
private inline fun <reified T> ConsumeResult(store: ResultStore, key: String, crossinline onResult: (T) -> Unit) {
    val result = store.getResult<T>(key)
    LaunchedEffect(result) {
        if (result != null) {
            onResult(result)
            store.removeResult<T>(key)
        }
    }
}
