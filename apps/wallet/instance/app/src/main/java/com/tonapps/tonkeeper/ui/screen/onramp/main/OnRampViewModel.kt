package com.tonapps.tonkeeper.ui.screen.onramp.main

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.model.legacy.BalanceEntity
import com.tonapps.blockchain.model.legacy.CurrencyCountries
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.OnrampsNative.OnrampsNativeType
import com.tonapps.extensions.MutableEffectFlow
import com.tonapps.extensions.singleValue
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.helper.TwinInput
import com.tonapps.tonkeeper.helper.TwinInput.Companion.opposite
import com.tonapps.tonkeeper.manager.assets.AssetsManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.onramp.main.entities.ProviderEntity
import com.tonapps.tonkeeper.ui.screen.onramp.main.state.OnRampPaymentMethodState
import com.tonapps.tonkeeper.ui.screen.onramp.main.state.UiState
import com.tonapps.tonkeeper.ui.screen.onramp.picker.currency.OnRampPickerScreen
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.OnRampArgsEntity
import com.tonapps.wallet.api.entity.OnRampMerchantEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.purchase.PurchaseRepository
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import uikit.UiButtonState
import uikit.extensions.collectFlow

class OnRampViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val settingsRepository: SettingsRepository,
    private val ratesRepository: RatesRepository,
    private val assetsManager: AssetsManager,
    private val purchaseRepository: PurchaseRepository,
    private val api: API,
    private val accountRepository: AccountRepository,
    private val environment: Environment,
): BaseWalletVM(app) {

    val installId: String
        get() = settingsRepository.installId

    private val settings = OnRampSettings(app)

    private val _sendValueFlow = MutableEffectFlow<Coins>()
    val sendValueFlow = _sendValueFlow.asSharedFlow().filterNotNull()

    private val _openWidgetFlow = MutableEffectFlow<ProviderEntity>()
    val openWidgetFlow = _openWidgetFlow.asSharedFlow().filterNotNull()

    private val _requestFocusFlow = MutableEffectFlow<TwinInput.Type?>()
    val requestFocusFlow = _requestFocusFlow.asSharedFlow().filterNotNull()

    private var observeInputButtonEnabledJob: Job? = null
    private var requestAvailableProvidersJob: Job? = null

    private val _selectedProviderIdFlow = MutableStateFlow<String?>(null)
    private val selectedProviderIdFlow = _selectedProviderIdFlow.asStateFlow()

    private val _availableProvidersFlow = MutableStateFlow(OnRampMerchantEntity.Data())
    private val availableProvidersFlow = _availableProvidersFlow.asStateFlow().filterNotNull()

    private val _selectedPaymentMethodFlow = MutableStateFlow(settings.getPaymentMethod())
    private val selectedPaymentMethodFlow = _selectedPaymentMethodFlow.asStateFlow()

    private val _stepFlow = MutableStateFlow(UiState.Step.Input)
    val stepFlow = _stepFlow.asStateFlow()

    private val twinInput = TwinInput(viewModelScope)

    private val onRampDataFlow = purchaseRepository
        .onRampDataFlow()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
        .filterNotNull()

    private val paymentMerchantsFlow = twinInput.stateFlow
        .map { it.sendCurrency.code.uppercase() }
        .distinctUntilChanged()
        .map(purchaseRepository::getPaymentMethods)

    private val paymentMethodsFlow = paymentMerchantsFlow.map { merchants ->
        merchants.map { it.methods }.flatten().distinctBy { it.type }.filter { it.type != "apple_pay" }
    }.distinctUntilChanged()

    private val merchantsFlow = flow {
        emit(purchaseRepository.getMerchants())
    }

    val country: String
        get() = environment.deviceCountry

    val providersFlow = combine(
        availableProvidersFlow,
        merchantsFlow.map { list ->
            list.associateBy { it.id }
        }
    ) { availableProviders, merchants ->
        (availableProviders.items + availableProviders.suggested).mapNotNull { widget ->
            merchants[widget.merchant]?.let { ProviderEntity(widget, it) }
        }
    }.flowOn(Dispatchers.IO)

    val inputPrefixFlow = twinInput.stateFlow.map { it.focus.opposite }.distinctUntilChanged()
    val sendOutputCurrencyFlow = twinInput.stateFlow.map { it.sendCurrency }.distinctUntilChanged()
    val receiveOutputCurrencyFlow = twinInput.stateFlow.map { it.receiveCurrency }.distinctUntilChanged()

    val allowedPairFlow = combine(
        twinInput.currenciesStateFlow,
        onRampDataFlow.map { it }.distinctUntilChanged()
    ) { (send, receive), data ->
        data.findValidPairs(send.symbol, receive.symbol).pair
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isChangellyFlow = allowedPairFlow.map { pairs ->
        val merchantSlugs = pairs?.merchants?.map { it.slug } ?: return@map false
        merchantSlugs.size == 1 && merchantSlugs.contains("changelly")
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val ratesFlow = twinInput.currenciesStateFlow.map { inputCurrencies ->
        val fiatCurrency = inputCurrencies.fiat ?: settingsRepository.currency
        val tokens = inputCurrencies.cryptoTokens.map { it.tokenQuery }
        ratesRepository.getRates(wallet.network, fiatCurrency, tokens)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null).filterNotNull()

    val minAmountFlow = combine(
        twinInput.stateFlow.map { it.send }.distinctUntilChanged(),
        allowedPairFlow.map { it?.min }.distinctUntilChanged()
    ) { sendValue, minAmount ->
        val coins = minAmount?.let {
            Coins.of(it, sendValue.decimals)
        } ?: return@combine null
        if (!coins.isPositive) {
            return@combine null
        }
        if (sendValue.coins >= coins || sendValue.coins.isZero) {
            return@combine null
        }
        val formatted = CurrencyFormatter.format(sendValue.symbol, coins, replaceSymbol = false)
        UiState.MinAmount(coins, formatted)
    }

    val rateFormattedFlow = combine(
        twinInput.currenciesStateFlow,
        ratesFlow
    ) { inputCurrencies, rates ->
        if (rates.isEmpty) {
            return@combine UiState.RateFormatted(null, null)
        }
        val value = Coins.ONE
        val firstLinePrefix = CurrencyFormatter.format(inputCurrencies.send.symbol, value, replaceSymbol = false)
        val firstRate = rates.convert(inputCurrencies.send, value, inputCurrencies.receive)
        if (!firstRate.isPositive) {
            return@combine UiState.RateFormatted(null, null)
        }
        val firstLineSuffix = CurrencyFormatter.format(inputCurrencies.receive.symbol, firstRate, replaceSymbol = false)
        val firstLine = "$firstLinePrefix ≈ $firstLineSuffix"
        val strippedFirstValue = Coins.of(CurrencyFormatter.format(value = firstRate).toString())

        val secondLinePrefix = CurrencyFormatter.format(inputCurrencies.receive.symbol, value, replaceSymbol = false)
        val secondRate = Coins.ONE / strippedFirstValue
        if (!secondRate.isPositive) {
            return@combine UiState.RateFormatted(null, null)
        }
        val secondLineSuffix = CurrencyFormatter.format(inputCurrencies.send.symbol, secondRate, replaceSymbol = false)
        val secondLine = "$secondLinePrefix ≈ $secondLineSuffix"
        UiState.RateFormatted(firstLine, secondLine)
    }.distinctUntilChanged()

    private val reductionFactor = 1.03f
    val sendOutputValueFlow = twinInput.createConvertFlow(ratesFlow, TwinInput.Type.Send, reductionFactor)
    val receiveOutputValueFlow = twinInput.createConvertFlow(ratesFlow, TwinInput.Type.Receive, reductionFactor)

    val balanceUiStateFlow = twinInput.stateFlow.map { it.send }.distinctUntilChanged().map { sendInput ->
        val token = if (sendInput.currency.fiat) null else assetsManager.getToken(wallet, sendInput.currency.address)
        val balance = token?.token?.balance
        val remainingFormat = createRemainingFormat(balance, sendInput.coins)
        UiState.Balance(
            balance = token?.token?.balance,
            insufficientBalance = remainingFormat.second,
            remainingFormat = remainingFormat.first
        )
    }.flowOn(Dispatchers.IO)

    val selectedProviderUiStateFlow = combine(
        providersFlow,
        selectedProviderIdFlow
    ) { providers, selectedProviderId ->
        UiState.SelectedProvider(
            send = twinInput.state.sendCurrency,
            receive = twinInput.state.receiveCurrency,
            providers = providers,
            selectedProviderId = selectedProviderId,
            sendAmount = twinInput.state.send.coins
        )
    }

    private val _inputButtonUiStateFlow = MutableStateFlow<UiButtonState>(UiButtonState.Default(false))
    private val inputButtonUiStateFlow = _inputButtonUiStateFlow.asStateFlow()

    private val _confirmButtonUiStateFlow = MutableStateFlow<UiButtonState>(UiButtonState.Default(true))
    val confirmButtonUiStateFlow = _confirmButtonUiStateFlow.asStateFlow()

    val buttonUiStateFlow = combine(
        stepFlow,
        inputButtonUiStateFlow,
        confirmButtonUiStateFlow,
    ) { step, inputButton, confirmButton ->
        if (step == UiState.Step.Input) {
            inputButton
        } else {
            confirmButton
        }
    }.distinctUntilChanged()

    val paymentMethodUiStateFlow = combine(
        selectedPaymentMethodFlow,
        paymentMethodsFlow,
        environment.countryFlow,
        twinInput.stateFlow.map { it.receiveCurrency }.filterNotNull()
    ) { selectedType, methods, country, receiveCurrency ->
        if (receiveCurrency.fiat) {
            OnRampPaymentMethodState(
                selectedType = selectedType,
                methods = emptyList()
            )
        } else {
            OnRampPaymentMethodState(
                selectedType = selectedType,
                methods = methods.map {
                    OnRampPaymentMethodState.createMethod(context, it, country)
                }.sortedBy { method ->
                    val index = OnRampPaymentMethodState.sortKeys.indexOf(method.type)
                    if (index == -1) Int.MAX_VALUE else index
                }
            )
        }
    }.distinctUntilChanged()

    val purchaseType: String
        get() {
            val state = twinInput.state
            return if (!state.sendCurrency.fiat && !state.receiveCurrency.fiat) {
                "swap"
            } else if (state.sendCurrency.fiat) {
                "buy"
            } else {
                "sell"
            }
        }

    val fromNetwork: String?
        get() = com.tonapps.wallet.data.purchase.OnRampUtils.normalizeType(twinInput.state.sendCurrency)

    val toNetwork: String?
        get() = com.tonapps.wallet.data.purchase.OnRampUtils.normalizeType(twinInput.state.receiveCurrency)

    val from: String
        get() = OnRampUtils.fixSymbol(twinInput.state.sendCurrency.symbol)

    val to: String
        get() = OnRampUtils.fixSymbol(twinInput.state.receiveCurrency.symbol)

    val fromForAnalytics: String
        get() = if (twinInput.state.sendCurrency.fiat) {
            "fiat"
        } else {
            "crypto_$from"
        }

    val toForAnalytics: String
        get() = if (twinInput.state.receiveCurrency.fiat) {
            "fiat"
        } else {
            "crypto_$to"
        }

    val paymentMethod: String?
        get() {
            if (purchaseType == "swap") {
                return null
            }
            return _selectedPaymentMethodFlow.value ?: "unknown"
        }

    init {
        applyDefaultCurrencies()
        // applyNetworkObserver()
        applyOverValueObserver()
        applyDefaultAmountObserver()
        startObserveInputButtonEnabled()

        combine(
            _selectedPaymentMethodFlow.filter { it == null },
            paymentMethodsFlow.filter { it.isNotEmpty() }
        ) { _, methods ->
            val hasCard = methods.any { it.type.equals(OnRampPaymentMethodState.CARD_TYPE, true) }
            if (hasCard) {
                setSelectedPaymentMethod(OnRampPaymentMethodState.CARD_TYPE)
            } else {
                setSelectedPaymentMethod(methods.first().type)
            }
        }.launch()
    }

    private fun applyOverValueObserver() {
        combine(
            providersFlow.filter { it.isNotEmpty() }.distinctUntilChanged(),
            selectedProviderIdFlow.filterNotNull().distinctUntilChanged()
        ) { providers, selectedProviderId ->
            providers.find { it.id == selectedProviderId }
        }.filter { provider ->
            provider != null && provider.minAmount > 0
        }.filterNotNull().onEach { provider ->
            val minValue = Coins.of(provider.minAmount, twinInput.state.send.decimals)
            if (twinInput.state.send.fiat && minValue > twinInput.state.send.coins) {
                forceProvider(provider.id, minValue)
            } else if (!twinInput.state.send.fiat) {
                val balance = balanceUiStateFlow.singleValue()?.balance?.value
                if (balance != null && minValue > balance) {
                    toast(Localization.insufficient_balance_title)
                } else {
                    forceProvider(provider.id, minValue)
                }
            }
        }.launch()
    }

    private fun forceProvider(id: String, value: Coins) {
        forceSendValue(value)
        setSelectedProviderId(id)
        requestAvailableProviders()
    }

    private fun applyDefaultAmountObserver() {
        combine(
            allowedPairFlow.map {
                com.tonapps.wallet.data.purchase.OnRampUtils.smartRoundUp(it?.min ?: 0.0)
            }.distinctUntilChanged(),
            balanceUiStateFlow.map { it.balance?.value },
        ) { minAmount, tokenBalance ->
            val min = Coins.of(minAmount, twinInput.state.send.decimals)
            val currentValue = twinInput.state.send.coins
            if (twinInput.state.send.currency.fiat && (currentValue.isZero || min > currentValue)) {
                min
            } else if (tokenBalance != null && tokenBalance >= min) {
                min
            } else {
                null
            }
        }.filterNotNull().distinctUntilChanged().onEach(::forceSendValue).launch()
    }

    private fun forceSendValue(coins: Coins) {
        twinInput.updateValue(TwinInput.Type.Send, Coins.string(coins))
        _sendValueFlow.tryEmit(coins)
    }

    /*private fun applyNetworkObserver() {
        combine(
            twinInput.stateFlow,
            onRampDataFlow.map { it.data }
        ) { state, data ->
            network = data.resolveNetwork(true, state.sendCurrency) ?: data.resolveNetwork(false, state.receiveCurrency) ?: "native"
        }.launch()
    }*/

    fun updateFocusInput(type: TwinInput.Type) {
        twinInput.updateFocus(type)
    }

    fun updateSendCurrency(currency: WalletCurrency, saveSettings: Boolean = true) {
        twinInput.updateCurrency(TwinInput.Type.Send, currency)
        if (saveSettings) {
            settings.setFromCurrency(currency)
        }
    }

    fun updateSendInput(amount: String) {
        twinInput.updateValue(TwinInput.Type.Send, amount)
    }

    fun updateReceiveInput(amount: String) {
        twinInput.updateValue(TwinInput.Type.Receive, amount)
    }

    fun updateReceiveCurrency(currency: WalletCurrency) {
        twinInput.updateCurrency(TwinInput.Type.Receive, currency)
        settings.setToCurrency(currency)
    }

    fun switch() {
        twinInput.switch()
        settings.setToCurrency(twinInput.state.receiveCurrency)
        settings.setFromCurrency(twinInput.state.sendCurrency)
    }

    fun reset() {
        cancelRequestAvailableProviders()
        startObserveInputButtonEnabled()
        _stepFlow.value = UiState.Step.Input
    }

    private fun startObserveInputButtonEnabled() {
        cancelObserveInputButtonEnabled()
        observeInputButtonEnabledJob = combine(
            balanceUiStateFlow,
            allowedPairFlow,
            twinInput.stateFlow,
            minAmountFlow.map { it?.amount ?: Coins.ZERO }.distinctUntilChanged(),
        ) { balance, pair, inputs, minAmount ->
            if (inputs.isEmpty || pair == null) {
                UiButtonState.Default(false)
            } else if (minAmount > inputs.send.coins) {
                UiButtonState.Default(false)
            } else if (inputs.sendCurrency.isTONChain || inputs.sendCurrency.isTronChain) {
                UiButtonState.Default(balance.balance != null && balance.balance.value.isPositive && !balance.insufficientBalance)
            } else {
                UiButtonState.Default(true)
            }
        }.distinctUntilChanged().collectFlow {
            _inputButtonUiStateFlow.value = it
        }
    }

    private fun cancelObserveInputButtonEnabled() {
        observeInputButtonEnabledJob?.cancel()
        observeInputButtonEnabledJob = null
    }

    private fun currencyByCountry(): WalletCurrency {
        val code = CurrencyCountries.getCurrencyCode(environment.deviceCountry)
        return WalletCurrency.ofOrDefault(code)
    }

    private fun applyDefaultCurrencies() {
        val fromCurrency = settings.getFromCurrency()
        updateSendCurrency(fromCurrency ?: currencyByCountry(), fromCurrency != null)
        updateReceiveCurrency(settings.getToCurrency() ?: WalletCurrency.TON)
    }

    fun pickCurrency(forType: TwinInput.Type) = viewModelScope.launch {
        runCatching {
            OnRampPickerScreen.run(
                context = context,
                wallet = wallet,
                currency = twinInput.getCurrency(forType),
                send = forType == TwinInput.Type.Send
            )
        }.onSuccess { currency ->
            if (currency == twinInput.state.getCurrency(forType)) {
                return@onSuccess
            } else if (twinInput.state.hasCurrency(currency)) {
                _requestFocusFlow.tryEmit(forType)
                switch()
            } else if (forType == TwinInput.Type.Send) {
                _requestFocusFlow.emit(twinInput.state.focus)
                updateSendCurrency(currency)
            } else if (forType == TwinInput.Type.Receive) {
                _requestFocusFlow.emit(twinInput.state.focus)
                updateReceiveCurrency(currency)
            }
        }
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

    private suspend fun requestWebViewLinkArg(paymentMethod: String?): OnRampArgsEntity {
        var walletAddress = ""
        if (twinInput.state.hasTonChain) {
            walletAddress = wallet.address
        } else if (twinInput.state.hasTronChain) {
            walletAddress = accountRepository.getTronAddress(wallet.id) ?: ""
        }

        return OnRampArgsEntity(
            from = from,
            to = to,
            fromNetwork = fromNetwork,
            toNetwork = toNetwork,
            wallet = walletAddress.trim(),
            purchaseType = purchaseType,
            amount = twinInput.state.send.coins,
            paymentMethod = paymentMethod
        )
    }

    private fun cancelRequestAvailableProviders() {
        requestAvailableProvidersJob?.cancel()
        requestAvailableProvidersJob = null
    }

    private fun setLoading(loading: Boolean) {
        if (_stepFlow.value == UiState.Step.Input && loading) {
            _inputButtonUiStateFlow.value = UiButtonState.Loading
        } else if (_stepFlow.value == UiState.Step.Confirm && loading) {
            _confirmButtonUiStateFlow.value = UiButtonState.Loading
        } else if (_stepFlow.value == UiState.Step.Confirm) {
            _confirmButtonUiStateFlow.value = UiButtonState.Default(true)
        }
    }

    fun requestAvailableProviders(selectedPaymentMethod: String? = _selectedPaymentMethodFlow.value) {
        if (twinInput.state.isEmpty) {
            return
        }

        cancelRequestAvailableProviders()
        cancelObserveInputButtonEnabled()
        setLoading(true)

        requestAvailableProvidersJob = collectFlow(
            paymentMethodsFlow.take(1)
        ) { paymentMethods ->
            try {
                if (paymentMethods.isEmpty()) {
                    throw IOException("No payment methods available")
                }
                val args = requestWebViewLinkArg(selectedPaymentMethod)
                val availableProviders = api.calculateOnRamp(args)
                if (availableProviders.isEmpty) {
                    throw IOException("No providers available for the selected payment method")
                } else if ((paymentMethods.size == 1 || args.withoutPaymentMethod) && availableProviders.size == 1) {
                    val provider = availableProviders.items.first()
                    val merchants = purchaseRepository.getMerchants()
                    val method = merchants.find { it.id == provider.merchant } ?: throw IOException("No merchant found for provider: ${provider.merchant}")
                    _openWidgetFlow.tryEmit(ProviderEntity(
                        widget = provider,
                        details = method
                    ))
                } else {
                    _availableProvidersFlow.value = availableProviders
                    _stepFlow.value = UiState.Step.Confirm
                    setLoading(false)
                }
            } catch (ignored: CancellationException) { } catch (e: Exception) {
                toast(Localization.unknown_error)
                reset()
            }
        }
    }

    fun setSelectedProviderId(id: String?) {
        _selectedProviderIdFlow.value = id
    }

    fun isPurchaseOpenConfirm(providerId: String) = settingsRepository.isPurchaseOpenConfirm(wallet.id, providerId)

    fun disableConfirmDialog(wallet: WalletEntity, providerId: String) {
        settingsRepository.disablePurchaseOpenConfirm(wallet.id, providerId)
    }

    fun trackContinueToProvider(providerName: String, providerDomain: String, txId: String?) {
        val nativeType = when (purchaseType) {
            "buy" -> OnrampsNativeType.Buy
            "sell" -> OnrampsNativeType.Sell
            else -> OnrampsNativeType.Swap
        }
        AnalyticsHelper.Default.events.onrampsNative.onrampContinueToProvider(
            txId = txId,
            type = nativeType,
            sellAssetNetwork = fromNetwork ?: from,
            sellAssetSymbol = from,
            sellAmount = twinInput.state.send.coins.value.toDouble(),
            buyAssetNetwork = toNetwork ?: to,
            buyAssetSymbol = to,
            buyAmount = twinInput.state.receive.coins.value.toDouble(),
            countryCode = country,
            paymentMethod = paymentMethod ?: "unknown",
            providerName = providerName,
            providerDomain = providerDomain
        )
    }

    fun setSelectedPaymentMethod(type: String) {
        _selectedPaymentMethodFlow.value = type
        requestAvailableProviders(type)
        settings.setPaymentMethod(type)
    }

    override fun onCleared() {
        super.onCleared()
        cancelRequestAvailableProviders()
        cancelObserveInputButtonEnabled()
    }
}