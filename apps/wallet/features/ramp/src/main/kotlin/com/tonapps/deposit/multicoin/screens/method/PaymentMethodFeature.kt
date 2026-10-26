package com.tonapps.deposit.multicoin.screens.method

import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.bus.core.contract.UtmParameters
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowFrom
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowWalletMode
import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.multicoin.analytics.DepositAnalytics
import com.tonapps.deposit.multicoin.analytics.RampAnalytics
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
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
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
    ) : PaymentMethodState {
        val methods: List<RampMethod> by lazyUnsafe {
            asset.methods.filter { method -> method.providers.any { it.fiat == fiatCode } }
        }

        val supportedFiatCodes: List<String> by lazyUnsafe {
            asset.methods
                .flatMap { method -> method.providers.map { it.fiat } }
                .distinct()
        }
    }
}

sealed interface PaymentMethodEvent {
    data class OpenP2P(val url: String) : PaymentMethodEvent
    data object ShowP2PError : PaymentMethodEvent
}

data class PaymentMethodFeatureData(
    val rampType: RampType,
    val assetId: String,
    val analyticsFrom: DepositFlowFrom,
    val preferredCurrency: String? = null,
    val utm: UtmParameters = UtmParameters.Empty,
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

    private val depositAnalytics = DepositAnalytics(data.rampType, data.analyticsFrom, DepositFlowWalletMode.Multi, data.utm)
    private val rampAnalytics = RampAnalytics.of(data.rampType, data.analyticsFrom, DepositFlowWalletMode.Multi, data.utm)
    private val viewTracked = AtomicBoolean(false)

    init {
        load()
    }

    fun retry() {
        load()
    }

    fun openP2PMethod() {
        bgScope.launch {
            val walletId = accountRepository.getSelectedWalletId() ?: return@launch
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

    fun trackPaymentMethodClick(method: RampMethod, fiatCode: String) {
        rampAnalytics.clickFiatPaymentMethod(
            cryptoAsset = data.assetId,
            fiatCode = fiatCode,
            paymentMethod = method.type.value,
        )
    }

    fun selectCurrency(currency: WalletCurrency) {
        val current = state.value as? PaymentMethodState.Data ?: return
        state.tryEmit(current.copy(fiatCode = currency.code))
    }

    private fun load() {
        state.tryEmit(PaymentMethodState.Loading)
        bgScope.launch {
            try {
                val asset = rampRepository.getAsset(data.rampType, data.assetId)
                val fiatCode = resolveFiatCode(asset)
                val loaded = PaymentMethodState.Data(
                    asset = asset,
                    fiatCode = fiatCode,
                )
                state.emit(loaded)
                trackViewFiatPaymentMethod(loaded)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                L.e(e)
                state.emit(PaymentMethodState.Empty)
            }
        }
    }

    private fun trackViewFiatPaymentMethod(loaded: PaymentMethodState.Data) {
        if (!viewTracked.compareAndSet(false, true)) {
            return
        }
        rampAnalytics.viewFiatPaymentMethod(
            cryptoAsset = data.assetId,
            fiatCode = loaded.fiatCode,
            availableOptions = loaded.methods.map { it.type.value },
        )
    }

    private suspend fun resolveFiatCode(asset: RampAssetDetail): String {
        val supportedFiats = asset.methods
            .flatMap { method -> method.providers.map { it.fiat } }
            .distinct()
        val currencyCodes = loadCurrencyCodes()
            .ifEmpty { supportedFiats }
            .filter { code -> supportedFiats.any { it.equals(code, true) } }
        val preferred = data.preferredCurrency ?: settingsRepository.currency.code
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

}
