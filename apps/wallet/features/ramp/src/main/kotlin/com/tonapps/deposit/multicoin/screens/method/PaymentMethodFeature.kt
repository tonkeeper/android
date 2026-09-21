package com.tonapps.deposit.multicoin.screens.method

import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.multicoin.analytics.DepositAnalytics
import com.tonapps.deposit.multicoin.data.RampAssetDetail
import com.tonapps.deposit.multicoin.data.RampMethod
import com.tonapps.deposit.multicoin.data.RampRepository
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.extensions.lazyUnsafe
import com.tonapps.log.L
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import io.exchangeapi.models.CreateP2PSessionRequest
import io.exchangeapi.models.ExchangeMerchantInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface PaymentMethodState {
    data object Loading : PaymentMethodState
    data object Empty : PaymentMethodState
    data class Data(
        val asset: RampAssetDetail,
        val fiatCode: String,
        val methods: List<RampMethod>,
        val isMethodsLoading: Boolean = false,
    ) : PaymentMethodState {
        val supportedFiatCodes: List<String> by lazyUnsafe {
            asset.methods
                .flatMap { method -> method.providers.map { it.fiat } }
                .distinct()
        }
    }
}

sealed interface PaymentMethodEvent {
    data class ShowP2PConfirmation(val merchant: ExchangeMerchantInfo) : PaymentMethodEvent
    data class OpenP2P(val url: String) : PaymentMethodEvent
    data object ShowP2PError : PaymentMethodEvent
}

data class PaymentMethodFeatureData(
    val rampType: RampType,
    val assetId: String,
    val analyticsFrom: DepositFlowFrom,
    val preferredCurrency: String? = null,
)

class PaymentMethodFeature(
    private val data: PaymentMethodFeatureData,
    private val rampRepository: RampRepository,
    private val exchangeRepository: ExchangeRepository,
    private val accountRepository: AccountRepository,
    private val mcAccountRepository: McAccountRepository,
    private val settingsRepository: SettingsRepository,
) : AsyncViewModel() {

    val state: StateFlow<PaymentMethodState> field = MutableStateFlow<PaymentMethodState>(PaymentMethodState.Loading)
    val events: SharedFlow<PaymentMethodEvent> field = MutableSharedFlow(extraBufferCapacity = 1)

    private val depositAnalytics = DepositAnalytics(data.rampType, data.analyticsFrom)

    private var selectedFiatCode: String? = null
    private var methodsJob: Job? = null

    init {
        load()
    }

    fun retry() {
        load()
    }

    /** Called when a P2P payment method is tapped instead of navigating to the amount screen. */
    fun checkP2PMethod() {
        bgScope.launch {
            val walletId = accountRepository.getSelectedWalletId() ?: return@launch
            if (settingsRepository.isPurchaseOpenConfirm(walletId, P2P_OPEN_CONFIRM_ID)) {
                val merchant = runCatching { exchangeRepository.getMerchants() }
                    .getOrDefault(emptyList())
                    .firstOrNull { it.id == WALLET_MERCHANT_ID }
                if (merchant != null) {
                    depositAnalytics.viewP2pAlert()
                    events.tryEmit(PaymentMethodEvent.ShowP2PConfirmation(merchant))
                    return@launch
                }
            }
            openP2P(walletId)
        }
    }

    fun allowP2P(doNotShowAgain: Boolean) {
        bgScope.launch {
            val walletId = accountRepository.getSelectedWalletId() ?: return@launch
            if (doNotShowAgain) {
                settingsRepository.disablePurchaseOpenConfirm(walletId, P2P_OPEN_CONFIRM_ID)
            }
            openP2P(walletId)
        }
    }

    private suspend fun openP2P(walletId: String) {
        val current = state.value as? PaymentMethodState.Data ?: return
        val address = mcAccountRepository.findAccount(walletId, data.assetId)?.data?.displayAddress
        if (address.isNullOrBlank()) {
            events.tryEmit(PaymentMethodEvent.ShowP2PError)
            return
        }
        try {
            val response = exchangeRepository.createP2PSession(
                CreateP2PSessionRequest(
                    wallet = address,
                    assetId = data.assetId,
                    fiatCurrency = current.fiatCode,
                )
            )
            depositAnalytics.continueToP2pMarket()
            events.tryEmit(PaymentMethodEvent.OpenP2P(response.deeplinkUrl))
        } catch (e: Throwable) {
            L.e(e)
            events.tryEmit(PaymentMethodEvent.ShowP2PError)
        }
    }

    fun selectCurrency(currency: WalletCurrency) {
        val current = state.value as? PaymentMethodState.Data ?: return
        if (current.fiatCode == currency.code) {
            return
        }
        selectedFiatCode = currency.code
        when (data.rampType) {
            RampType.RampOff -> state.tryEmit(
                current.copy(fiatCode = currency.code, methods = current.asset.methods.forFiat(currency.code))
            )

            RampType.RampOn -> {
                state.tryEmit(current.copy(fiatCode = currency.code, isMethodsLoading = true))
                methodsJob?.cancel()
                methodsJob = bgScope.launch {
                    val methods = try {
                        loadMethods(current.asset, currency.code)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        L.e(e)
                        emptyList()
                    }
                    val latest = state.value as? PaymentMethodState.Data ?: return@launch
                    if (latest.fiatCode != currency.code) {
                        return@launch
                    }
                    state.emit(latest.copy(methods = methods, isMethodsLoading = false))
                }
            }
        }
    }

    private fun load() {
        state.tryEmit(PaymentMethodState.Loading)
        methodsJob?.cancel()
        bgScope.launch {
            try {
                val (asset, currencyCodes) = coroutineScope {
                    val asset = async { rampRepository.getAsset(data.rampType, data.assetId) }
                    val currencyCodes = async { loadCurrencyCodes() }
                    asset.await() to currencyCodes.await()
                }
                val fiatCode = resolveFiatCode(asset, currencyCodes)
                val methods = loadMethods(asset, fiatCode)
                state.emit(
                    PaymentMethodState.Data(
                        asset = asset,
                        fiatCode = fiatCode,
                        methods = methods,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                L.e(e)
                state.emit(PaymentMethodState.Empty)
            }
        }
    }

    private suspend fun loadMethods(asset: RampAssetDetail, fiatCode: String): List<RampMethod> {
        val methods = when (data.rampType) {
            RampType.RampOn -> rampRepository.getAsset(data.rampType, data.assetId, fiat = fiatCode).methods
            RampType.RampOff -> asset.methods
        }
        return methods.forFiat(fiatCode)
    }

    private fun List<RampMethod>.forFiat(fiatCode: String): List<RampMethod> {
        return filter { method -> method.providers.any { it.fiat == fiatCode } }
    }

    private fun resolveFiatCode(asset: RampAssetDetail, appCurrencyCodes: List<String>): String {
        val supportedFiats = asset.methods
            .flatMap { method -> method.providers.map { it.fiat } }
            .distinct()
        val currencyCodes = appCurrencyCodes
            .ifEmpty { supportedFiats }
            .filter { code -> supportedFiats.any { it.equals(code, true) } }
        val preferred = selectedFiatCode ?: data.preferredCurrency ?: settingsRepository.currency.code
        return currencyCodes.firstOrNull { it.equals(preferred, true) }
            ?: currencyCodes.firstOrNull { it.equals(WalletCurrency.USD.code, true) }
            ?: currencyCodes.firstOrNull()
            ?: WalletCurrency.USD.code
    }

    private suspend fun loadCurrencyCodes(): List<String> {
        return try {
            val wallet = accountRepository.forceSelectedWallet()
            exchangeRepository
                .getCurrencies(wallet.network, settingsRepository.getLocale(), wallet.multichainWalletId)
                .map { it.code }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            L.e(e)
            emptyList()
        }
    }

    companion object {
        private const val P2P_OPEN_CONFIRM_ID = "p2p"
        private const val WALLET_MERCHANT_ID = "wallet"
    }
}
