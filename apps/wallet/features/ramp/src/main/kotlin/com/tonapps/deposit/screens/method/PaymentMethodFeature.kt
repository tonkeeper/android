package com.tonapps.deposit.screens.method

import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowSellAsset
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowBuyAsset
import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.data.assetsOfType
import com.tonapps.deposit.data.pairedCryptoNetworkInfos
import com.tonapps.deposit.data.resolveAssetFromDeeplink
import com.tonapps.deposit.screens.network.CryptoNetworkInfo
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.deposit.toBuyAsset
import com.tonapps.deposit.toSellAsset
import com.tonapps.extensions.lazyUnsafe
import com.tonapps.log.L
import com.tonapps.mvi.MviFeature
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviState
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.props.MviProperty
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import io.exchangeapi.models.CreateP2PSessionRequest
import io.exchangeapi.models.ExchangeLayout
import io.exchangeapi.models.ExchangeLayoutItemType
import io.exchangeapi.models.ExchangeMerchantInfo

sealed interface PaymentMethodAction : MviAction {
    data object Init : PaymentMethodAction
    data class SelectCurrency(val currency: WalletCurrency) : PaymentMethodAction
    data object CheckP2PMethod : PaymentMethodAction
    data class AllowP2P(val isDontShowAgain: Boolean) : PaymentMethodAction
}

sealed interface PaymentMethodEvent {
    data class ShowP2PConfirmation(val merchant: ExchangeMerchantInfo) : PaymentMethodEvent
    data class OpenP2P(val url: String) : PaymentMethodEvent
    data class AutoNavigateCrypto(val resolvedAsset: RampAsset, val targetCurrency: WalletCurrency) : PaymentMethodEvent
    data class AutoNavigateCash(val resolvedAsset: RampAsset, val paymentMethodType: String, val fiatCurrency: WalletCurrency) : PaymentMethodEvent
}

sealed interface PaymentMethodState : MviState {
    data object Loading : PaymentMethodState
    data object Empty : PaymentMethodState

    data class Data(
        val asset: RampAsset,
        val currencies: List<WalletCurrency>,
        val selectedCurrency: WalletCurrency?,
        val paymentMethods: List<PaymentMethodItem>,
        val cryptoAssets: List<WalletCurrency>,
        val stablecoinAssets: List<WalletCurrency>,
        val stablecoinNetworks: Map<String, List<CryptoNetworkInfo>> = emptyMap(),
        val sectionFilter: PaymentMethodSectionFilter = PaymentMethodSectionFilter.All,
    ) : PaymentMethodState {
        val showCashSection: Boolean get() = paymentMethods.isNotEmpty()
                && (sectionFilter == PaymentMethodSectionFilter.All
                || sectionFilter == PaymentMethodSectionFilter.CashOnly)
        val showCryptoSection: Boolean get() = cryptoAssets.isNotEmpty()
                && (sectionFilter == PaymentMethodSectionFilter.All
                || sectionFilter == PaymentMethodSectionFilter.CryptoOnly)
        val showStablecoinSection: Boolean get() = stablecoinAssets.isNotEmpty()
                && (sectionFilter == PaymentMethodSectionFilter.All
                || sectionFilter == PaymentMethodSectionFilter.StablecoinOnly)
    }
}

data class PaymentMethodItem(
    val type: String,
    val title: String,
    val subtitle: String?,
    val iconUrl: String,
) {
    val isP2P: Boolean by lazyUnsafe {
        type.equals("p2p", true)
    }
}

class PaymentMethodViewState(
    val global: MviProperty<PaymentMethodState>
) : MviViewState

@kotlinx.serialization.Serializable
enum class PaymentMethodSectionFilter {
    All, CashOnly, CryptoOnly, StablecoinOnly
}

data class PaymentMethodFeatureData(
    val initialAsset: RampAsset? = null,
    val rampType: RampType,
    val ft: String? = null,
    val tn: String? = null,
    val tt: String? = null,
    val fn: String? = null,
    val cm: String? = null,
    val sectionFilter: PaymentMethodSectionFilter = PaymentMethodSectionFilter.All,
    val preferredCurrency: String? = null,
)

class PaymentMethodFeature(
    private val data: PaymentMethodFeatureData,
    private val exchangeRepository: ExchangeRepository,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val api: API,
) : MviFeature<PaymentMethodAction, PaymentMethodState, PaymentMethodViewState>(
    initState = PaymentMethodState.Loading,
    initAction = PaymentMethodAction.Init
) {
    val rampType: RampType get() = data.rampType

    private lateinit var asset: RampAsset

    /** Resolved asset, available after loadData completes. Used by router for manual navigation. */
    val resolvedAsset: RampAsset? get() = if (::asset.isInitialized) asset else null

    init {
        if (data.initialAsset != null) asset = data.initialAsset
    }

    private val relay = MviRelay<PaymentMethodEvent>()
    val events = relay.events

    override fun createViewState(): PaymentMethodViewState {
        return buildViewState {
            PaymentMethodViewState(mviProperty { it })
        }
    }

    override suspend fun executeAction(action: PaymentMethodAction) {
        when (action) {
            is PaymentMethodAction.Init -> loadData()
            is PaymentMethodAction.SelectCurrency -> applyCurrencySelection(action.currency)
            is PaymentMethodAction.CheckP2PMethod -> {
                trackP2PView()
                val wallet = accountRepository.getSelectedWallet() ?: return
                val shouldValidated = settingsRepository.isPurchaseOpenConfirm(wallet.id, P2P_OPEN_CONFIRM_ID)
                if (shouldValidated) {
                    val merchant = exchangeRepository.getMerchants().firstOrNull { it.id == WALLET_MERCHANT_ID }
                    if (merchant != null) {
                        relay.emit(PaymentMethodEvent.ShowP2PConfirmation(merchant))
                        return
                    }
                }

                val url = createP2PSession(wallet.address)
                if (url != null) {
                    relay.emit(PaymentMethodEvent.OpenP2P(url))
                }
            }

            is PaymentMethodAction.AllowP2P -> {
                val wallet = accountRepository.getSelectedWallet() ?: return
                if (action.isDontShowAgain) {
                    settingsRepository.disablePurchaseOpenConfirm(wallet.id, P2P_OPEN_CONFIRM_ID)
                }
                val url = createP2PSession(wallet.address)
                if (url != null) {
                    relay.emit(PaymentMethodEvent.OpenP2P(url))
                }
            }
        }
    }

    fun selectCurrency(currency: WalletCurrency) {
        sendAction(PaymentMethodAction.SelectCurrency(currency))
    }

    private suspend fun loadData() {
        setState { PaymentMethodState.Loading }
        try {
            val wallet = accountRepository.forceSelectedWallet()
            val preferredCode = data.preferredCurrency
            val fiatCode = settingsRepository.currency.code.uppercase()
            val currencies =
                exchangeRepository.getCurrencies(wallet.network, settingsRepository.getLocale())

            val selectedCurrency = (if (preferredCode != null) currencies.find { it.code.equals(preferredCode, true) } else null)
                ?: currencies.find { it.code.equals(fiatCode, true) }
                ?: currencies.firstOrNull()

            val layout = exchangeRepository.getLayoutCurrency(rampType, selectedCurrency)

            if (!::asset.isInitialized) {
                val currency = layout.resolveAssetFromDeeplink(data.ft, data.tn)
                    ?: throw IllegalStateException("Cannot resolve asset from deeplink params ft=${data.ft} tn=${data.tn}")
                asset = RampAsset.Currency(currency)
            }

            val (paymentMethods, crypto, stablecoins, networks) = resolveLayout(layout)

            setState {
                PaymentMethodState.Data(
                    asset = asset,
                    currencies = currencies,
                    selectedCurrency = selectedCurrency,
                    paymentMethods = paymentMethods,
                    cryptoAssets = crypto,
                    stablecoinAssets = stablecoins,
                    stablecoinNetworks = networks,
                    sectionFilter = data.sectionFilter,
                )
            }

            emitDeeplinkNavigation(layout, selectedCurrency)
        } catch (e: Throwable) {
            L.e(e)
            setState { PaymentMethodState.Empty }
        }
    }

    private suspend fun emitDeeplinkNavigation(layout: ExchangeLayout, fiatCurrency: WalletCurrency?) {
        if (data.tt != null) {
            val targetCurrency = layout.resolveAssetFromDeeplink(data.tt, data.fn) ?: return
            relay.emit(PaymentMethodEvent.AutoNavigateCrypto(asset, targetCurrency))
        } else if (data.cm != null && fiatCurrency != null) {
            relay.emit(PaymentMethodEvent.AutoNavigateCash(asset, data.cm, fiatCurrency))
        }
    }

    private suspend fun createP2PSession(address: String): String? {
        val state = obtainSpecificState<PaymentMethodState.Data>()
        val fiatCurrency = state?.selectedCurrency?.code ?: return null
        val request = CreateP2PSessionRequest(
            wallet = address,
            network = asset.network ?: "NATIVE",
            cryptoCurrency = asset.currencyCode,
            fiatCurrency = fiatCurrency,
        )
        try {
            val response = exchangeRepository.createP2PSession(request)
            return response.deeplinkUrl
        } catch (e: Throwable) {
            L.e(e)
            return null
        }
    }

    private suspend fun applyCurrencySelection(currency: WalletCurrency) {
        val prevState = obtainSpecificState<PaymentMethodState.Data>() ?: return
        setState { PaymentMethodState.Loading }
        try {
            exchangeRepository.clearRampCache(rampType)
            val layout = exchangeRepository.getLayoutCurrency(rampType, currency)
            val (paymentMethods, crypto, stablecoins, networks) = resolveLayout(layout)

            setState {
                prevState.copy(
                    selectedCurrency = currency,
                    paymentMethods = paymentMethods,
                    cryptoAssets = crypto,
                    stablecoinAssets = stablecoins,
                    stablecoinNetworks = networks,
                )
            }
        } catch (e: Throwable) {
            L.e(e)
            setState { prevState.copy(selectedCurrency = currency) }
        }
    }

    private fun resolveLayout(layout: ExchangeLayout): LayoutResult {
        val layoutAsset = layout.assetsOfType(ExchangeLayoutItemType.fiat).find {
            it.symbol.equals(asset.currencyCode, ignoreCase = true)
                    && it.network.equals(asset.network ?: "native", ignoreCase = true)
        }

        val paymentMethods = layoutAsset?.cashMethods?.map { method ->
            PaymentMethodItem(
                type = method.type.value,
                title = method.name,
                subtitle = getSubtitle(method.type.value, api.country),
                iconUrl = method.image,
            )
        } ?: emptyList()

        val allCrypto = layout.pairedCryptoNetworkInfos(asset.currencyCode, asset.network)
        val (crypto, stablecoins, networks) = splitCryptoAssets(allCrypto)

        return if (asset.isStablecoin) {
            LayoutResult(paymentMethods, crypto, stablecoins, networks)
        } else {
            LayoutResult(paymentMethods, allCrypto.map { it.currency }, emptyList(), networks)
        }
    }

    private data class LayoutResult(
        val paymentMethods: List<PaymentMethodItem>,
        val cryptoAssets: List<WalletCurrency>,
        val stablecoinAssets: List<WalletCurrency>,
        val stablecoinNetworks: Map<String, List<CryptoNetworkInfo>>,
    )

    private fun splitCryptoAssets(
        allCrypto: List<CryptoNetworkInfo>
    ): Triple<List<WalletCurrency>, List<WalletCurrency>, Map<String, List<CryptoNetworkInfo>>> {
        val stablecoins = allCrypto.filter { it.currency.isStablecoin }
        val crypto = allCrypto.filter { !it.currency.isStablecoin }.map { it.currency }

        val networks = stablecoins.groupBy { it.currency.code.uppercase() }
        val grouped = stablecoins.map { it.currency }.distinctBy { it.code.uppercase() }

        return Triple(crypto, grouped, networks)
    }

    private fun trackP2PView() {
        val events = AnalyticsHelper.Default.events
        when (rampType) {
            RampType.RampOn -> events.depositFlow.depositViewP2p(
                buyAsset = asset.toCurrency.toBuyAsset(),
                sellAsset = DepositFlowSellAsset.Fiat,
            )
            RampType.RampOff -> events.withdrawFlow.withdrawViewP2p(
                sellAsset = asset.toCurrency.toSellAsset(),
                buyAsset = WithdrawFlowBuyAsset.Fiat,
            )
        }
    }

    companion object {
        private val P2P_OPEN_CONFIRM_ID = "p2p"
        private val WALLET_MERCHANT_ID = "wallet"

        private fun getSubtitle(type: String, country: String): String? {
            if (!type.equals("card", true)) {
                return null
            }

            return if (country.equals("ru", true)) "МИР" else "Visa, Mastercard"
        }
    }
}
