package com.tonapps.deposit.screens.ramp.amount

import com.tonapps.blockchain.model.legacy.BalanceEntity
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.WithdrawFlow.WithdrawFlowBuyAsset
import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.data.assetsOfType
import com.tonapps.deposit.screens.method.RampAsset
import com.tonapps.deposit.screens.provider.ProviderItem
import com.tonapps.deposit.screens.provider.ProviderQuote
import com.tonapps.deposit.screens.provider.ProviderRate
import com.tonapps.deposit.screens.provider.ProviderWithQuote
import com.tonapps.deposit.toBuyAsset
import com.tonapps.deposit.toSellAsset
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.log.L
import com.tonapps.mvi.MviFeature
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.MviSubject
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviState
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.props.MviProperty
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import io.exchangeapi.models.ExchangeCalculateRequest
import io.exchangeapi.models.ExchangeCalculation
import io.exchangeapi.models.ExchangeDirection
import io.exchangeapi.models.ExchangeFlow
import io.exchangeapi.models.ExchangeLayoutItemType
import io.exchangeapi.models.ExchangePaymentMethodType
import io.exchangeapi.models.ExchangeQuote
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import java.math.BigDecimal

private val ExchangeDirection?.isBuy get() = this == ExchangeDirection.buy
private val ExchangeDirection?.isSell get() = this == ExchangeDirection.sell

sealed interface DepositAmountAction : MviAction {
    object Init : DepositAmountAction
    // object ToggleInputCurrency : DepositAmountAction
    class SelectProvider(val providerId: String) : DepositAmountAction
    class CheckProvider(val provider: ProviderWithQuote) : DepositAmountAction
    class AllowProvider(val provider: ProviderWithQuote, val isDontShowAgain: Boolean) : DepositAmountAction
}

sealed interface DepositAmountState : MviState {
    data object Loading : DepositAmountState

    data class Data(
        val asset: RampAsset,
        val paymentMethodType: String,
        val currencyCode: String,
        val fiatCode: String,
        val balance: BalanceEntity? = null,
        val purchaseType: ExchangeDirection? = null,
        val providers: List<ProviderWithQuote> = emptyList(),
        val defaultAmount: String,
        val initialAmount: String? = null,
        val selectedProviderId: String? = null,
        val isCalculating: Boolean = false,
        val selectedErrorProvider: ProviderItem? = null,
    ) : DepositAmountState {
        val selectedProvider: ProviderWithQuote?
            get() = providers.find { it.info.id == selectedProviderId }

        val canContinue: Boolean
            get() = !isCalculating && selectedProvider?.quote?.widgetUrl != null

        // buy = input fiat, sell = input crypto
        val inputCurrencyCode: String
            get() = if (purchaseType.isBuy) fiatCode else currencyCode

        val receiveCurrencyCode: String
            get() = if (purchaseType.isBuy) currencyCode else fiatCode
    }
}

sealed interface DepositAmountEvent {
    data object ShowConfirmation : DepositAmountEvent
    class Continue(val providerUrl: String) : DepositAmountEvent
}

class DepositAmountViewState(
    val global: MviProperty<DepositAmountState>
) : MviViewState

@OptIn(FlowPreview::class)
class DepositAmountFeature(
    val data: RampAmountData,
    private val onRampRepository: ExchangeRepository,
    private val settingsRepository: SettingsRepository,
    private val accountRepository: AccountRepository,
    private val tokenRepository: TokenRepository,
) : MviFeature<DepositAmountAction, DepositAmountState, DepositAmountViewState>(
    initState = DepositAmountState.Loading,
    initAction = DepositAmountAction.Init
) {

    private val relay = MviRelay<DepositAmountEvent>()
    val events = relay.events

    private val amountSubject = MviSubject<String>()

    init {
        amountSubject.events
            .debounce(300)
            .distinctUntilChanged()
            .mapLatest { amount -> onAmountChanged(amount) }
            .launchIn(stateScope)
    }

    override fun createViewState(): DepositAmountViewState {
        return buildViewState {
            DepositAmountViewState(mviProperty { it })
        }
    }

    override suspend fun executeAction(action: DepositAmountAction) {
        when (action) {
            is DepositAmountAction.Init -> loadData()
            // is DepositAmountAction.ToggleInputCurrency -> toggleInputCurrency()
            is DepositAmountAction.SelectProvider -> selectProvider(action.providerId)
            is DepositAmountAction.CheckProvider -> {
                val wallet = accountRepository.getSelectedWallet() ?: return
                val shouldValidated = settingsRepository.isPurchaseOpenConfirm(wallet.id, action.provider.info.id)
                if (shouldValidated) {
                    relay.emit(DepositAmountEvent.ShowConfirmation)
                } else {
                    onContinue(action.provider)
                }
            }

            is DepositAmountAction.AllowProvider -> {
                val wallet = accountRepository.getSelectedWallet() ?: return
                if (action.isDontShowAgain) {
                    settingsRepository.disablePurchaseOpenConfirm(wallet.id, action.provider.info.id)
                }

                onContinue(action.provider)
            }
        }
    }

    private fun onContinue(provider: ProviderWithQuote) {
        runCatching {
            provider.quote?.let { quote ->
                val sellAmount = quote.amount.value.toDouble()

                if (data.purchaseType.isSell) {
                    AnalyticsHelper.Default.events.withdrawFlow.withdrawClickOnrampContinue(
                        sellAsset = data.assetFrom.toCurrency.toSellAsset(),
                        providerName = provider.info.title,
                        sellAmount = sellAmount
                    )
                } else {
                    AnalyticsHelper.Default.events.depositFlow.depositClickOnrampContinue(
                        buyAsset = data.assetTo.toCurrency.toBuyAsset(),
                        providerName = provider.info.title,
                        buyAmount = quote.receiveAmount?.toDoubleOrNull() ?: 0.0
                    )
                }
            }
        }

        val quote = provider.quote ?: return
        val url = quote.widgetUrl
        val txId = quote.merchantTransactionId ?: ""

        runCatching {
            if (data.purchaseType.isSell) {
                AnalyticsHelper.Default.events.withdrawFlow.withdrawViewOnrampFlow(
                    sellAsset = data.assetFrom.toCurrency.toSellAsset(),
                    providerName = provider.info.title,
                    sellAmount = quote.amount.value.toDouble(),
                    buyAsset = WithdrawFlowBuyAsset.Fiat,
                    txId = txId
                )
            } else {
                AnalyticsHelper.Default.events.depositFlow.depositViewOnrampFlow(
                    buyAsset = data.assetTo.toCurrency.toBuyAsset(),
                    providerName = provider.info.title,
                    buyAmount = quote.receiveAmount?.toDoubleOrNull() ?: 0.0,
                    txId = txId
                )
            }
        }

        relay.emit(DepositAmountEvent.Continue(url))
    }

    fun onAmountInput(amount: String) {
        amountSubject.emit(amount)
    }

    private suspend fun loadData() {
        val cryptoCode = data.cryptoCode
        val fiatCode = data.fiatCode
        try {
            val wallet = accountRepository.forceSelectedWallet()

            val minValue = try {
                onRampRepository.getLayoutCurrency(data.rampType, data.fiatAsset.toCurrency)
                    .assetsOfType(ExchangeLayoutItemType.fiat)
                    .firstOrNull {
                        it.networkName == data.cryptoAsset.toCurrency.title
                                && it.symbol == data.cryptoAsset.toCurrency.symbol
                    }
                    ?.cashMethods
                    ?.firstOrNull { it.type.value == data.paymentMethodType }
                    ?.providers
                    ?.mapNotNull { it.limits?.min?.toBigDecimal() }
                    ?.filter { it > BigDecimal.ZERO }
                    ?.minOrNull()
            } catch (e: Throwable) {
                L.e(e)
                null
            }

            val fromCurrency = data.assetFrom.toCurrency
            val balance = if (data.purchaseType.isSell) {
                val tronAddress = if (fromCurrency.isTronChain) accountRepository.getTronAddress(wallet.id) else null
                tokenRepository.get(fromCurrency, wallet.accountId, wallet.network, tronAddress = tronAddress)
                    ?.firstOrNull { it.address.equals(fromCurrency.address, ignoreCase = true) && it.balance.isTransferable }
                    ?.balance
            } else {
                null
            }

            val merchants = onRampRepository.getMerchants()

            val providerItems = merchants.map { merchant ->
                ProviderWithQuote(
                    info = ProviderItem(
                        id = merchant.id,
                        title = merchant.title,
                        imageUrl = merchant.image,
                        isBest = false,
                        buttons = merchant.buttons,
                    )
                )
            }

            val initialAmountStr = if (data.purchaseType.isSell) null else minValue?.toString()

            setState {
                DepositAmountState.Data(
                    asset = data.assetTo,
                    paymentMethodType = data.paymentMethodType,
                    currencyCode = cryptoCode,
                    fiatCode = fiatCode,
                    purchaseType = data.purchaseType,
                    providers = providerItems,
                    defaultAmount = defaultAmount(if (data.purchaseType.isBuy) cryptoCode else fiatCode),
                    initialAmount = initialAmountStr,
                    balance = balance,
                )
            }

            if (initialAmountStr != null) {
                amountSubject.emit(initialAmountStr)
            }
        } catch (e: Throwable) {
            L.e(e)
            setState {
                DepositAmountState.Data(
                    asset = data.assetTo,
                    paymentMethodType = data.paymentMethodType,
                    currencyCode = cryptoCode,
                    fiatCode = fiatCode,
                    purchaseType = data.purchaseType,
                    providers = emptyList(),
                    defaultAmount = defaultAmount(if (data.purchaseType.isBuy) cryptoCode else fiatCode),
                )
            }
        }
    }

    private suspend fun onAmountChanged(amountText: String) {
        val amount = try {
            val coins = Coins.of(amountText)
            if (coins.isPositive) coins else null
        } catch (_: Throwable) {
            null
        }

        val wallet = accountRepository.getSelectedWallet()
            ?: return

        val address = if (data.purchaseType.isSell) {
            when (data.assetFrom.toCurrency.isTronChain) {
                true -> accountRepository.getTronAddress(wallet.id) ?: return
                false -> wallet.address
            }
        } else {
            when (data.assetTo.toCurrency.isTronChain) {
                true -> accountRepository.getTronAddress(wallet.id) ?: return
                false -> wallet.address
            }
        }

        if (amount == null) {
            setState<DepositAmountState.Data> {
                copy(
                    isCalculating = false,
                    providers = providers.map { it.copy(rate = null, quote = null) }
                )
            }
            return
        }

        if (data.purchaseType.isSell) {
            AnalyticsHelper.Default.events.withdrawFlow.withdrawViewOnrampInsertAmount(
                sellAsset = data.assetFrom.toCurrency.toSellAsset(),
                providerName = data.paymentMethodType
            )
        } else {
            AnalyticsHelper.Default.events.depositFlow.depositViewOnrampInsertAmount(
                buyAsset = data.assetTo.toCurrency.toBuyAsset(),
                providerName = data.paymentMethodType
            )
        }

        setState<DepositAmountState.Data> {
            copy(isCalculating = true, selectedErrorProvider = null)
        }

        val stateData = obtainSpecificState<DepositAmountState.Data>() ?: return

        try {
            val paymentMethodType = ExchangePaymentMethodType.decode(stateData.paymentMethodType)

            val fromCode = data.assetFrom.currencyCode
            val toCode = data.assetTo.currencyCode
            val result = onRampRepository.calculate(
                ExchangeCalculateRequest(
                    from = fromCode,
                    to = toCode,
                    amount = Coins.string(amount),
                    wallet = address,
                    purchaseType = data.purchaseType,
                    fromNetwork = data.fromNetwork,
                    toNetwork = data.toNetwork,
                    paymentMethod = paymentMethodType,
                    flow = if (data.purchaseType.isSell) ExchangeFlow.withdraw else ExchangeFlow.deposit
                )
            )

            val quotesMap = buildQuotesMap(result)
            val allQuotesMap = (result.items + result.suggested).associateBy { it.merchant.value }
            val bestProviderId = result.items.firstOrNull()?.merchant?.value
            val calculateProviderIds = allQuotesMap.keys

            setState<DepositAmountState.Data> {
                var selectedProvider: ProviderWithQuote? = null

                val updatedProviders = providers
                    .filter { it.info.id in calculateProviderIds }
                    .map { entry ->
                        val onRampQuote = quotesMap[entry.info.id]
                        val exchangeQuote = allQuotesMap[entry.info.id]
                        val widgetUrl = onRampQuote?.widgetUrl

                        val isAvailable = onRampQuote != null && widgetUrl != null

                        val providerRate = exchangeQuote?.let {
                            val receiveCoins = Coins.of(it.amount)
                            val rateBase = if (isAvailable) {
                                amount
                            } else {
                                val min = it.minAmount?.let { v -> Coins.of(v) }
                                val max = it.maxAmount?.let { v -> Coins.of(v) }
                                when {
                                    min != null && amount < min -> min
                                    max != null && amount > max -> max
                                    else -> min ?: max ?: amount
                                }
                            }
                            val ratePerUnit = if (rateBase.isPositive) receiveCoins / rateBase else Coins.ZERO
                            ProviderRate(
                                rateFormatted = formatRate(ratePerUnit),
                            )
                        }

                        if (isAvailable) {
                            val receiveCoins = Coins.of(onRampQuote.amount)
                            entry.copy(
                                info = entry.info.copy(isBest = entry.info.id == bestProviderId),
                                rate = providerRate,
                                quote = ProviderQuote(
                                    amount = amount,
                                    receiveCoins = receiveCoins,
                                    currencyCode = currencyCode,
                                    widgetUrl = widgetUrl,
                                    receiveAmount = defaultAmount(
                                        receiveCurrencyCode,
                                        receiveCoins
                                    ),
                                    merchantTransactionId = onRampQuote.merchantTransactionId,
                                ),
                            )
                        } else {
                            val minAmount = exchangeQuote?.minAmount?.let { Coins.of(it) } ?: Coins.ZERO
                            val maxAmount = exchangeQuote?.maxAmount?.let { Coins.of(it) }
                            entry.copy(
                                info = entry.info.copy(
                                    isBest = false,
                                    minAmount = minAmount,
                                    maxAmount = maxAmount,
                                ),
                                rate = providerRate,
                                quote = null,
                            )
                        }
                    }

                if (selectedProvider == null) {
                    selectedProvider = updatedProviders.firstOrNull { it.info.isBest && it.quote != null }
                }

                val errorProvider = if (selectedProvider == null) {
                    val suggestedWithMin = result.suggested
                        .filter { it.minAmount != null }
                        .minByOrNull { Coins.of(it.minAmount!!) }
                    val suggestedWithMax = result.suggested
                        .filter { it.maxAmount != null }
                        .maxByOrNull { Coins.of(it.maxAmount!!) }

                    val suggestedEntry = suggestedWithMin ?: suggestedWithMax
                    suggestedEntry?.let { entry ->
                        val info = updatedProviders.find { it.info.id == entry.merchant.value }?.info
                            ?: providers.firstOrNull()?.info
                        info?.copy(
                            minAmount = entry.minAmount?.let { Coins.of(it) } ?: Coins.ZERO,
                            maxAmount = entry.maxAmount?.let { Coins.of(it) },
                        )
                    } ?: providers.firstOrNull()?.info
                } else {
                    null
                }

                copy(
                    providers = updatedProviders,
                    selectedProviderId = selectedProvider?.info?.id,
                    isCalculating = false,
                    selectedErrorProvider = errorProvider,
                )
            }
        } catch (e: Throwable) {
            L.e(e)
            setState<DepositAmountState.Data> {
                copy(
                    isCalculating = false,
                    providers = providers.map { it.copy(rate = null, quote = null) }
                )
            }
        }
    }

    private fun formatRate(ratePerUnit: Coins): String {
        val fromCode =
            if (data.purchaseType.isBuy) data.fiatCode else data.cryptoCode
        val toCode =
            if (data.purchaseType.isBuy) data.cryptoCode else data.fiatCode
        return "1 $fromCode ≈ ${defaultAmount(toCode, ratePerUnit)}"
    }

    private fun defaultAmount(fiatCode: String, coin: Coins = Coins.ZERO): String {
        return CurrencyFormatter.format(fiatCode, coin, replaceSymbol = false)
            .toString()
    }

    private fun buildQuotesMap(calculation: ExchangeCalculation): Map<String, ExchangeQuote> {
        return calculation.items.associateBy { it.merchant.value }
    }

    // private fun toggleInputCurrency() {
    //     setState<DepositAmountState.Data> {
    //         copy(isInputCrypto = !isInputCrypto)
    //     }
    // }

    private fun selectProvider(providerId: String) {
        setState<DepositAmountState.Data> {
            copy(selectedProviderId = providerId)
        }
    }
}
