package com.tonapps.tonkeeper.ui.screen.onramp.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.ton.extensions.equalsAddress
import com.tonapps.extensions.toUriOrNull
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.core.AnalyticsHelper
import com.tonapps.tonkeeper.extensions.getNormalizeCountryFlow
import com.tonapps.tonkeeper.extensions.getProvidersByCountry
import com.tonapps.tonkeeper.extensions.onRampDataFlow
import com.tonapps.tonkeeper.extensions.onRampFormCurrencyFlow
import com.tonapps.tonkeeper.extensions.providersFlow
import com.tonapps.tonkeeper.helper.BrowserHelper
import com.tonapps.tonkeeper.manager.assets.AssetsManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.onramp.main.state.CurrencyInputState
import com.tonapps.tonkeeper.ui.screen.onramp.main.state.OnRampBalanceState
import com.tonapps.tonkeeper.ui.screen.onramp.main.state.OnRampConfirmState
import com.tonapps.tonkeeper.ui.screen.onramp.main.state.OnRampCurrencyInputs
import com.tonapps.tonkeeper.ui.screen.onramp.main.state.OnRampInputsState
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.BalanceEntity
import com.tonapps.wallet.api.entity.OnRampArgsEntity
import com.tonapps.wallet.api.entity.OnRampMerchantEntity
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.data.purchase.PurchaseRepository
import com.tonapps.wallet.data.purchase.entity.OnRamp.AllowedPair
import com.tonapps.wallet.data.purchase.entity.PurchaseMethodEntity
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.rates.entity.RatesEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

class OnRampViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val settingsRepository: SettingsRepository,
    private val ratesRepository: RatesRepository,
    private val assetsManager: AssetsManager,
    private val purchaseRepository: PurchaseRepository,
    private val tokenRepository: TokenRepository,
    private val api: API,
    private val accountRepository: AccountRepository
): BaseWalletVM(app) {

    private var requestWebViewLinkJob: Job? = null
    private val formCurrencyFlow = purchaseRepository.onRampFormCurrencyFlow(settingsRepository)

    private val onRampResultFlow = purchaseRepository
        .onRampDataFlow(settingsRepository, api)

    val installId: String
        get() = settingsRepository.installId

    private val _currencyInputStateFlow = MutableStateFlow(OnRampCurrencyInputs())

    @OptIn(FlowPreview::class)
    val currencyInputStateFlow = _currencyInputStateFlow.asStateFlow().debounce(100)
    val currencyInputState: OnRampCurrencyInputs
        get() = _currencyInputStateFlow.value

    private val _rawValuesFlow = MutableStateFlow(Pair(0.0, 0.0))
    private val rawValuesFlow = _rawValuesFlow.asStateFlow()
    private val rawValues: Pair<Double, Double>
        get() = _rawValuesFlow.value

    private val _inputValuesFlow = MutableStateFlow(OnRampInputsState())
    val inputValuesFlow = _inputValuesFlow.asStateFlow()
    private val inputValues: OnRampInputsState
        get() = _inputValuesFlow.value

    private val _confirmStateFlow = MutableStateFlow(OnRampConfirmState())
    val confirmStateFlow = _confirmStateFlow.asStateFlow()

    private val confirmState: OnRampConfirmState
        get() = _confirmStateFlow.value

    val balanceFlow = combine(inputValuesFlow, currencyInputStateFlow) { (from, to), state ->
        val balance = if (state.sell is CurrencyInputState.TONAsset) getBalanceToken(state.sell) else null
        val remainingFormat = createRemainingFormat(balance, from)

        OnRampBalanceState(
            balance = balance,
            insufficientBalance = remainingFormat.second,
            remainingFormat = remainingFormat.first
        )
    }

    val isFirstButtonEnabled: Boolean
        get() {
            if (currencyInputState.allowedPair == null) {
                return false
            }
            return (rawValues.first > 0 || rawValues.second > 0)
        }

    val isSecondButtonEnabled: Boolean
        get() {
            if (!isFirstButtonEnabled) {
                return false
            }
            return !confirmState.unavailable
        }

    init {
        settingsRepository.countryFlow.collectFlow(::setCountry)
        combine(
            formCurrencyFlow,
            onRampResultFlow,
        ) { (send, receive), onRampResult ->
            val onRampData = onRampResult.data
            val providers = purchaseRepository.getProvidersByCountry(wallet, settingsRepository, onRampResult.country)
            val sendInputState = createCurrencyInputState(send)
            val receiveInputState = createCurrencyInputState(receive)
            val rates = requestRates(sendInputState, receiveInputState)
            val allowedPair = if (send.fiat && receive.fiat) null else onRampData.isValidPair(OnRampCurrencyInputs.fixSymbol(send.code), OnRampCurrencyInputs.fixSymbol(receive.code))
            val allowedProviderIds = allowedPair?.merchants?.map { it.slug } ?: emptyList()
            val selectedProviderId = allowedProviderIds.firstOrNull()
            _currencyInputStateFlow.value = OnRampCurrencyInputs(
                country = onRampResult.country,
                sell = sendInputState,
                buy = receiveInputState,
                providers = providers.filter { allowedProviderIds.contains(it.id) },
                rates = rates,
                selectedProviderId = selectedProviderId,
                allowedPair = allowedPair,
                merchants = onRampData.merchants.filter { allowedProviderIds.contains(it.slug) },
                availableFiatSlugs = onRampData.availableFiatSlugs,
            )
        }.launch()

        combine(currencyInputStateFlow, rawValuesFlow) { state, values ->
            if (state.allowedPair == null) {
                _inputValuesFlow.value = OnRampInputsState(
                    from = Coins.of(values.first, state.sellDecimals),
                    to = Coins.of(values.second, state.buyDecimals)
                )
            } else if (values.second == 0.0) {
                val coins = Coins.of(values.first, state.sellDecimals)
                if (state.sell.isUSD && state.buy.isUSDT) {
                    _inputValuesFlow.value = OnRampInputsState(
                        from = coins,
                        to = coins
                    )
                } else {
                    _inputValuesFlow.value = OnRampInputsState(
                        from = coins,
                        to = state.rateConvert(coins, false)
                    )
                }
            } else {
                val coins = Coins.of(values.second, state.buyDecimals)
                if (state.sell.isUSD && state.buy.isUSDT) {
                    _inputValuesFlow.value = OnRampInputsState(
                        from = coins,
                        to = coins
                    )
                } else {
                    _inputValuesFlow.value = OnRampInputsState(
                        from = state.rateConvert(coins, true),
                        to = coins
                    )
                }
            }
        }.flowOn(Dispatchers.IO).launch()

        combine(
            currencyInputStateFlow,
            inputValuesFlow
        ) { inputs, values ->
            val toFormat = CurrencyFormatter.format(inputs.from, values.from, replaceSymbol = false)
            val fromFormat = CurrencyFormatter.format(inputs.to, values.to, replaceSymbol = false)

            _confirmStateFlow.update {
                it.copy(
                    fromFormat = fromFormat,
                    toFormat = toFormat
                )
            }
        }.launch()
    }

    private suspend fun createCurrencyInputState(currency: WalletCurrency = WalletCurrency.USD): CurrencyInputState {
        if (currency.fiat) {
            return CurrencyInputState.Fiat(currency)
        }
        val chain = currency.chain
        if (chain is WalletCurrency.Chain.TON) {
            val token = tokenRepository.getToken(
                accountId = wallet.accountId,
                testnet = wallet.testnet,
                tokenAddress = chain.address
            ) ?: return createCurrencyInputState()

            return CurrencyInputState.TONAsset(token)
        } else {
            return CurrencyInputState.Crypto(currency)
        }
    }

    fun calculate() {
        _confirmStateFlow.update {
            it.copy(
                loading = true,
                unavailable = false,
            )
        }
        requestWebViewLink()
    }

    private suspend fun requestWebViewLinkArg(): OnRampArgsEntity {
        var walletAddress = ""
        if (currencyInputState.isTON) {
            walletAddress = wallet.address
        } else if (currencyInputState.isTron) {
            walletAddress = accountRepository.getTronAddress(wallet.id) ?: ""
        }
        val args = OnRampArgsEntity(
            from = currencyInputState.from,
            to = currencyInputState.to,
            network = currencyInputState.type,
            wallet = walletAddress,
            purchaseType = currencyInputState.purchaseType,
            amount = inputValues.from,
            country = currencyInputState.country,
            paymentMethod = currencyInputState.paymentType
        )

        return args
    }

    private fun requestWebViewLink() {
        requestWebViewLinkJob?.cancel()
        requestWebViewLinkJob = viewModelScope.launch {
            val arg = requestWebViewLinkArg()
            val items = api.calculateOnRamp(arg)
            val selectedProvider = currencyInputState.selectedProvider
            if (selectedProvider == null) {
                setConfirmUnavailable()
                return@launch
            }
            val sortMerchants = items.map { it.merchant }
            val item = items.firstOrNull {
                it.merchant.equals(selectedProvider.id, ignoreCase = true)
            } ?: items.firstOrNull()

            if (item == null) {
                setConfirmUnavailable()
                return@launch
            }

            _currencyInputStateFlow.update { state ->
                state.copy(
                    selectedProviderId = item.merchant
                )
            }

            val coins = Coins.of(item.amount)

            _confirmStateFlow.update {
                it.copy(
                    loading = false,
                    unavailable = false,
                    fromFormat = CurrencyFormatter.format(currencyInputState.to, coins, replaceSymbol = false),
                    webViewLink = item.widgetUrl
                )
            }

            if (sortMerchants.size > 1) {
                _currencyInputStateFlow.update { state ->
                    val providers = state.providers
                        .filter { provider ->
                            sortMerchants.any { it.equals(provider.id, ignoreCase = true) }
                        }
                        .sortedBy { provider ->
                            sortMerchants.indexOfFirst { it.equals(provider.id, ignoreCase = true) }
                        }

                    val selectedProviderId = if (providers.any {
                        it.id.equals(state.selectedProviderId, ignoreCase = true)
                    }) {
                        state.selectedProviderId
                    } else {
                        providers.firstOrNull()?.id
                    }

                    state.copy(
                        providers = providers,
                        selectedProviderId = selectedProviderId
                    )
                }
            }
        }
    }

    private fun setConfirmUnavailable() {
        _confirmStateFlow.update {
            it.copy(
                loading = false,
                unavailable = true
            )
        }
    }

    fun resetConfirm() {
        _confirmStateFlow.update {
            it.copy(
                loading = false,
                unavailable = false,
            )
        }

        requestWebViewLinkJob?.cancel()
        requestWebViewLinkJob = null
    }

    fun openWeb() {
        val confirmState = confirmStateFlow.value
        val webViewLink = confirmState.webViewLink?.toUriOrNull() ?: return
        BrowserHelper.open(context, webViewLink)

        AnalyticsHelper.onRampOpenWebview(
            installId = installId,
            type = currencyInputState.purchaseType,
            sellAsset = currencyInputState.fromForAnalytics,
            buyAsset = currencyInputState.toForAnalytics,
            countryCode = currencyInputState.country,
            paymentMethod = currencyInputState.paymentType ?: "unknown",
            providerName = currencyInputState.selectedProvider?.title ?: "unknown",
            providerDomain = webViewLink.host ?: "unknown"
        )
    }

    fun setProviderId(providerId: String): Boolean {
        if (providerId.equals(currencyInputState.selectedProviderId, ignoreCase = true)) {
            return false
        }
        _currencyInputStateFlow.update {
            it.copy(
                selectedProviderId = providerId,
            )
        }
        return true
    }

    fun setPaymentMethod(id: String): Boolean {
        if (currencyInputState.paymentType.equals(id, ignoreCase = true)) {
            return false
        }
        _currencyInputStateFlow.update {
            it.copy(
                paymentType = id,
            )
        }
        return true
    }

    private suspend fun getBalanceToken(currency: CurrencyInputState.TONAsset): BalanceEntity? {
        val tokens = assetsManager.getTokens(wallet, settingsRepository.currency, false)
        val token = tokens.firstOrNull {
            it.address.equalsAddress(currency.address)
        } ?: return null
        return token.token.balance
    }

    private fun createRemainingFormat(balance: BalanceEntity?, value: Coins): Pair<CharSequence?, Boolean> {
        if (balance == null || value.isZero) {
            return Pair(null, false)
        }
        val remaining = balance.value - value
        if (remaining == balance.value) {
            return Pair(null, false)
        }
        val format = CurrencyFormatter.format(balance.token.symbol, remaining)
        return Pair(format, remaining.isNegative)
    }

    private suspend fun requestRates(
        send: CurrencyInputState,
        receive: CurrencyInputState
    ): RatesEntity = withContext(Dispatchers.IO) {
        val walletCurrency = CurrencyInputState.findWalletCurrency(send, receive) ?: settingsRepository.currency
        val token = CurrencyInputState.findToken(send, receive) ?: TokenEntity.TON
        ratesRepository.getRates(walletCurrency, token.address)
    }

    fun setFromAmount(value: Double) {
        _rawValuesFlow.update {
            it.copy(value, 0.0)
        }
    }

    fun setToAmount(value: Double) {
        _rawValuesFlow.update {
            it.copy(0.0, value)
        }
    }

    fun switch() {
        val currentSend = purchaseRepository.sendCurrency ?: WalletCurrency.TON
        val currentReceive = purchaseRepository.receiveCurrency

        purchaseRepository.sendCurrency = currentReceive
        purchaseRepository.receiveCurrency = currentSend

        _inputValuesFlow.update {
            it.copy(
                from = it.to,
                to = it.from
            )
        }
    }

    fun setCountry(country: String) {
        _currencyInputStateFlow.update {
            it.copy(country = country)
        }
    }

    private companion object {
        private val epsilon = BigDecimal("0.0009")
    }
}