package com.tonapps.deposit.multicoin.screens.ramp.amount

import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowDepositScreen
import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowWalletMode
import com.tonapps.core.components.AmountToggle
import com.tonapps.core.components.withLocaleDecimalSeparator
import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.multicoin.analytics.RampAnalytics
import com.tonapps.deposit.multicoin.fiatScale
import com.tonapps.deposit.multicoin.data.RampQuote
import com.tonapps.deposit.multicoin.data.RampRepository
import com.tonapps.deposit.screens.provider.ProviderItem
import com.tonapps.deposit.screens.provider.ProviderQuote
import com.tonapps.deposit.screens.provider.ProviderRate
import com.tonapps.deposit.screens.provider.ProviderWithQuote
import com.tonapps.deposit.screens.provider.unavailableLimit
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.deposit.utils.AmountError
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.log.L
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import io.exchangeapi.models.ExchangePaymentMethodType
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface RampAmountState {
    data object Loading : RampAmountState

    data class Data(
        val rampType: RampType,
        val symbol: String,
        val imageUrl: String,
        val paymentMethodType: ExchangePaymentMethodType,
        val fiatCode: String,
        val cryptoCode: String,
        val cryptoDecimals: Int,
        val address: String,
        val balanceCoins: Coins? = null,
        val balanceFormatted: String? = null,
        val providers: List<ProviderWithQuote> = emptyList(),
        val selectedProviderId: String? = null,
        val isCalculating: Boolean = false,
        val isContinuing: Boolean = false,
        val isCryptoInput: Boolean = false,
        val requestAmount: Coins? = null,
        val quotedInputAmount: Coins? = null,
        val quotedInputIsCrypto: Boolean = false,
        val cryptoPerFiat: BigDecimal? = null,
        val amountError: AmountError? = null,
        val convertedAmount: String = "",
        val initialAmount: String? = null,
    ) : RampAmountState {

        val isBuy: Boolean get() = rampType == RampType.RampOn

        val inputCurrencyCode: String get() = if (isBuy && !isCryptoInput) { fiatCode } else { cryptoCode }
        val receiveCurrencyCode: String get() = if (isBuy) { cryptoCode } else { fiatCode }
        val limitCurrencyCode: String get() = if (isBuy) { fiatCode } else { cryptoCode }

        val canToggleInput: Boolean get() = isBuy && cryptoPerFiat != null

        val selectedProvider: ProviderWithQuote?
            get() = providers.find { it.info.id == selectedProviderId }

        val canContinue: Boolean
            get() = !isCalculating && !isContinuing && address.isNotBlank() && amountError == null && selectedProvider?.quote != null

        fun isQuotedFor(inputAmount: Coins): Boolean {
            return quotedInputIsCrypto == isCryptoInput && inputAmount.isSame(quotedInputAmount)
        }
    }
}

sealed interface RampAmountEvent {
    data class Continue(val widgetUrl: String) : RampAmountEvent
    data class UpdateAmount(val text: String) : RampAmountEvent
}

@OptIn(FlowPreview::class)
class RampAmountFeature(
    private val data: RampAmountData,
    private val rampRepository: RampRepository,
    private val exchangeRepository: ExchangeRepository,
    private val mcAccountRepository: McAccountRepository,
    private val settingsRepository: SettingsRepository,
) : AsyncViewModel() {

    private data class AmountInput(val text: String, val isCrypto: Boolean)

    val state: StateFlow<RampAmountState> field = MutableStateFlow<RampAmountState>(RampAmountState.Loading)

    val events: SharedFlow<RampAmountEvent> field = MutableSharedFlow(extraBufferCapacity = 1)

    private val amountInput = MutableSharedFlow<AmountInput>(extraBufferCapacity = 1)

    private var quotesByMerchant: Map<String, RampQuote> = emptyMap()

    @Volatile
    private var amountToggle: AmountToggle? = null

    @Volatile
    private var pickedProviderId: String? = null

    private var continueJob: Job? = null

    private val analytics = RampAnalytics.of(
        data.rampType, data.analyticsFrom, DepositFlowWalletMode.Multi, data.utm, DepositFlowDepositScreen.AddFunds,
    )
    private val viewTracked = AtomicBoolean(false)

    init {
        amountInput
            .debounce(300)
            .distinctUntilChanged()
            .onEach { onAmountChanged(it) }
            .launchIn(stateScope)
        load()
    }

    fun onAmountInput(amount: String) {
        val current = state.value as? RampAmountState.Data ?: return
        amountInput.tryEmit(AmountInput(amount, current.isCryptoInput))
    }

    fun selectProvider(providerId: String, limitAmount: Coins?) {
        val current = state.value as? RampAmountState.Data ?: return
        val picked = current.providers.find { it.info.id == providerId } ?: return
        if (limitAmount == null) {
            if (picked.quote != null) {
                pickedProviderId = providerId
                state.postExactData { copy(selectedProviderId = providerId) }
            }
            return
        }
        val (target, roundingMode) = when (val limit = picked.info.unavailableLimit(current.requestAmount)) {
            is AmountError.BelowMin -> limit.minAmount to RoundingMode.CEILING
            is AmountError.AboveMax -> limit.maxAmount to RoundingMode.FLOOR
            null -> return
        }
        pickedProviderId = providerId
        val amount = if (current.isBuy) {
            target.value.setScale(0, roundingMode)
        } else {
            target.value
        }
        val text = amount.stripTrailingZeros().toPlainString().withLocaleDecimalSeparator()
        amountToggle = null
        state.postExactData { copy(selectedProviderId = providerId, isCryptoInput = false) }
        events.tryEmit(RampAmountEvent.UpdateAmount(text))
        amountInput.tryEmit(AmountInput(text, isCrypto = false))
    }

    fun toggleInputCurrency(text: String) {
        val current = state.value as? RampAmountState.Data ?: return
        val cryptoPerFiat = current.cryptoPerFiat
        if (!current.isBuy || cryptoPerFiat == null) {
            return
        }
        val toFiat = current.isCryptoInput
        val toggle = amountToggle?.toggledBack(data.assetId, text, toFiat) ?: AmountToggle(
            assetId = data.assetId,
            sourceText = text,
            toggledText = convertInputText(text, toFiat, cryptoPerFiat, fiatScale(current.fiatCode), current.cryptoDecimals),
            toFiat = toFiat,
        )
        amountToggle = toggle
        state.postExactData { copy(isCryptoInput = !toFiat) }
        events.tryEmit(RampAmountEvent.UpdateAmount(toggle.toggledText))
        amountInput.tryEmit(AmountInput(toggle.toggledText, isCrypto = !toFiat))
    }

    fun continueWithProvider() {
        if (continueJob?.isActive == true) {
            return
        }
        val current = state.value as? RampAmountState.Data ?: return
        if (!current.canContinue) {
            return
        }
        val provider = current.selectedProvider ?: return
        val quote = provider.quote ?: return
        state.postExactData { copy(isContinuing = true) }
        continueJob = bgScope.launch {
            analytics.clickRampInsertAmountContinue(
                cryptoAsset = data.assetId,
                fiatCode = current.fiatCode,
                paymentMethod = current.paymentMethodType.value,
                providerName = provider.info.title,
                amount = quote.amount.value.toDouble(),
            )
            if (!createOrder(current, provider.info.id)) {
                state.postExactData { copy(isContinuing = false) }
            }
        }
    }

    private fun load() {
        bgScope.launch {
            try {
                val assetDetail = rampRepository.getAsset(data.walletId, data.rampType, data.assetId, fiat = data.fiat)
                val method = assetDetail.methods.firstOrNull { it.type.value == data.paymentMethodType }
                    ?: assetDetail.methods.firstOrNull()
                    ?: throw IllegalStateException("No payment method for ${data.assetId}")

                val requestedFiat = data.fiat ?: settingsRepository.currency.code
                val fiatCode = requestedFiat.takeIf { requested -> method.providers.any { it.fiat == requested } }
                    ?: method.providers.firstOrNull()?.fiat
                    ?: requestedFiat
                val fiatProviders = method.providers.filter { it.fiat == fiatCode }

                val account = mcAccountRepository.findAccount(data.walletId, data.assetId)
                val address = account?.data?.displayAddress.orEmpty()

                val balanceCoins = if (data.rampType == RampType.RampOff) {
                    account?.let { Coins.of(it.displayBalance.fmt()) }
                } else {
                    null
                }
                val balanceFormatted = if (data.rampType == RampType.RampOff) { account?.formattedBalance } else { null }

                val initialAmount = if (data.rampType == RampType.RampOn) {
                    fiatProviders
                        .mapNotNull { it.limits?.min?.toBigDecimalOrNull() }
                        .filter { it > BigDecimal.ZERO }
                        .maxOrNull()
                        ?.setScale(0, RoundingMode.CEILING)
                        ?.toPlainString()
                } else {
                    null
                }

                val merchants = runCatching { exchangeRepository.getMerchants(data.walletId) }.getOrDefault(emptyList())
                    .associateBy { it.id }

                val providers = fiatProviders
                    .map { it.merchant }
                    .distinct()
                    .map { merchant ->
                        val info = merchants[merchant.value]
                        ProviderWithQuote(
                            info = ProviderItem(
                                id = merchant.value,
                                title = info?.title ?: merchant.value,
                                imageUrl = info?.image.orEmpty(),
                                isBest = false,
                            )
                        )
                    }

                state.tryEmit(
                    RampAmountState.Data(
                        rampType = data.rampType,
                        symbol = assetDetail.symbol,
                        imageUrl = assetDetail.image.orEmpty(),
                        paymentMethodType = method.type,
                        fiatCode = fiatCode,
                        cryptoCode = assetDetail.symbol,
                        cryptoDecimals = assetDetail.decimals,
                        address = address,
                        balanceCoins = balanceCoins,
                        balanceFormatted = balanceFormatted,
                        providers = providers,
                        initialAmount = initialAmount,
                    ).withConvertedAmount()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                L.e(e)
                state.tryEmit(RampAmountState.Loading)
            }
        }
    }

    private suspend fun onAmountChanged(input: AmountInput) {
        val current = state.value as? RampAmountState.Data ?: return
        if (current.isCryptoInput != input.isCrypto) {
            return
        }

        val inputAmount = parseAmount(input.text)
        val amount = inputAmount?.let { requestAmount(input, it, current) }

        if (inputAmount == null || amount == null) {
            quotesByMerchant = emptyMap()
            state.postExactData {
                copy(
                    isCalculating = false,
                    requestAmount = null,
                    quotedInputAmount = null,
                    amountError = null,
                    providers = providers.map { it.copy(rate = null, quote = null) },
                )
            }
            return
        }

        state.postExactData { copy(isCalculating = true, requestAmount = amount) }

        try {
            val quotes = rampRepository.getQuotes(
                walletId = data.walletId,
                rampType = current.rampType,
                assetId = data.assetId,
                fiat = current.fiatCode,
                amount = Coins.string(amount),
                paymentMethod = current.paymentMethodType,
            )

            val servingItems = quotes.items.filter { it.isWithinLimits(amount) }
            val itemsMap = servingItems.associateBy { it.merchant.value }
            quotesByMerchant = itemsMap
            val suggestedMap = (quotes.suggested + quotes.items).associateBy { it.merchant.value }
            val bestId = servingItems.firstOrNull()?.merchant?.value

            state.postExactData {
                if (isCryptoInput != input.isCrypto) {
                    return@postExactData this
                }
                val updated = providers.map { entry ->
                    val item = itemsMap[entry.info.id]
                    val suggested = suggestedMap[entry.info.id]
                    if (item != null) {
                        val receiveCoins = Coins.of(item.amountOut)
                        entry.copy(
                            info = entry.info.copy(
                                isBest = entry.info.id == bestId,
                                minAmount = item.minAmount?.let { Coins.of(it) } ?: Coins.ZERO,
                                maxAmount = item.maxAmount?.let { Coins.of(it) },
                            ),
                            rate = ProviderRate(rateFormatted = formatRate(item, this)),
                            quote = ProviderQuote(
                                amount = amount,
                                receiveCoins = receiveCoins,
                                currencyCode = receiveCurrencyCode,
                                widgetUrl = "",
                                receiveAmount = formatAmount(receiveCurrencyCode, receiveCoins),
                                merchantTransactionId = item.merchantTransactionId,
                            ),
                        )
                    } else {
                        entry.copy(
                            info = entry.info.copy(
                                isBest = false,
                                minAmount = suggested?.minAmount?.let { Coins.of(it) } ?: Coins.ZERO,
                                maxAmount = suggested?.maxAmount?.let { Coins.of(it) },
                            ),
                            rate = suggested?.let { ProviderRate(rateFormatted = formatRate(it, this)) },
                            quote = null,
                        )
                    }
                }

                val serving = updated.firstOrNull { it.info.id == pickedProviderId && it.quote != null }
                    ?: updated.firstOrNull { it.info.isBest && it.quote != null }
                    ?: updated.firstOrNull { it.quote != null }
                val closest = if (serving == null) { closestProvider(updated, amount) } else { null }
                val shown = serving ?: closest
                val withBest = if (closest == null) {
                    updated
                } else {
                    updated.map { it.copy(info = it.info.copy(isBest = it.info.id == closest.info.id)) }
                }
                val servingRate = serving?.let { itemsMap[it.info.id] }?.let { parseRate(it.rate) }
                val closestRate = closest?.let { suggestedMap[it.info.id] }?.let { parseRate(it.rate) }

                copy(
                    providers = withBest,
                    selectedProviderId = shown?.info?.id,
                    isCalculating = false,
                    quotedInputAmount = inputAmount,
                    quotedInputIsCrypto = input.isCrypto,
                    cryptoPerFiat = if (isBuy) { servingRate ?: cryptoPerFiat ?: closestRate } else { null },
                    amountError = closest?.let { amountError(it.info, amount) },
                )
            }
            (state.value as? RampAmountState.Data)?.let { updated ->
                trackViewRampInsertAmount(updated, updated.selectedProvider ?: updated.providers.firstOrNull())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e)
            quotesByMerchant = emptyMap()
            state.postExactData {
                copy(
                    isCalculating = false,
                    amountError = null,
                    providers = providers.map { it.copy(rate = null, quote = null) },
                )
            }
        }
    }

    private fun requestAmount(input: AmountInput, inputAmount: Coins, current: RampAmountState.Data): Coins? {
        if (!current.isBuy || !input.isCrypto) {
            return inputAmount
        }
        val toggle = amountToggle
        if (toggle != null && !toggle.toFiat && toggle.toggledText == input.text) {
            return parseAmount(toggle.sourceText)
        }
        val cryptoPerFiat = current.cryptoPerFiat ?: return null
        return Coins.of(inputAmount.value.divide(cryptoPerFiat, fiatScale(current.fiatCode), RoundingMode.HALF_UP))
            .takeIf { it.isPositive }
    }

    private fun closestProvider(providers: List<ProviderWithQuote>, amount: Coins): ProviderWithQuote? {
        val belowMin = providers
            .filter { it.info.minAmount.isPositive && amount < it.info.minAmount }
            .minByOrNull { it.info.minAmount }
        if (belowMin != null) {
            return belowMin
        }
        val aboveMax = providers
            .filter { entry -> entry.info.maxAmount?.let { amount > it } == true }
            .maxByOrNull { it.info.maxAmount ?: Coins.ZERO }
        return aboveMax ?: providers.firstOrNull()
    }

    private fun amountError(provider: ProviderItem, amount: Coins): AmountError? {
        return limitError(amount, provider.minAmount, provider.maxAmount)
    }

    private suspend fun createOrder(current: RampAmountState.Data, providerId: String): Boolean {
        val quote = quotesByMerchant[providerId] ?: return false
        val provider = current.providers.find { it.info.id == providerId } ?: return false
        val providerQuote = provider.quote ?: return false
        if (current.address.isBlank()) {
            return false
        }

        return try {
            val widgetUrl = rampRepository.createOrder(
                walletId = data.walletId,
                rampType = current.rampType,
                assetId = data.assetId,
                fiat = current.fiatCode,
                amount = Coins.string(providerQuote.amount),
                address = current.address,
                paymentMethod = quote.paymentMethod,
                merchant = quote.merchant,
                merchantTransactionId = quote.merchantTransactionId,
            )

            analytics.continueToRampProvider(
                cryptoAsset = data.assetId,
                fiatCode = current.fiatCode,
                paymentMethod = current.paymentMethodType.value,
                providerName = provider.info.title,
                amount = providerQuote.amount.value.toDouble(),
                txId = quote.merchantTransactionId,
            )
            if (events.subscriptionCount.value > 0) {
                events.emit(RampAmountEvent.Continue(widgetUrl))
                true
            } else {
                false
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e)
            false
        }
    }

    private fun trackViewRampInsertAmount(current: RampAmountState.Data, provider: ProviderWithQuote?) {
        if (provider == null || !viewTracked.compareAndSet(false, true)) {
            return
        }
        analytics.viewRampInsertAmount(
            cryptoAsset = data.assetId,
            fiatCode = current.fiatCode,
            paymentMethod = current.paymentMethodType.value,
            providerName = provider.info.title,
        )
    }

    private fun formatRate(quote: RampQuote, data: RampAmountState.Data): String {
        val perUnit = runCatching { Coins.of(quote.rate) }.getOrDefault(Coins.ZERO)
        return "1 ${data.limitCurrencyCode} ≈ ${formatAmount(data.receiveCurrencyCode, perUnit)}"
    }

    private inline fun MutableStateFlow<RampAmountState>.postExactData(
        crossinline setter: RampAmountState.Data.() -> RampAmountState.Data,
    ) {
        update { current -> (current as? RampAmountState.Data)?.setter()?.withConvertedAmount() ?: current }
    }

    private companion object {

        private fun parseAmount(text: String): Coins? {
            return try {
                Coins.of(text.replace(',', '.')).takeIf { it.isPositive }
            } catch (_: Throwable) {
                null
            }
        }

        private fun limitError(amount: Coins, minAmount: Coins, maxAmount: Coins?): AmountError? {
            return when {
                minAmount.isPositive && amount < minAmount -> AmountError.BelowMin(minAmount)
                maxAmount != null && amount > maxAmount -> AmountError.AboveMax(maxAmount)
                else -> null
            }
        }

        private fun RampQuote.isWithinLimits(amount: Coins): Boolean {
            val min = minAmount?.let { Coins.of(it) } ?: Coins.ZERO
            return limitError(amount, min, maxAmount?.let { Coins.of(it) }) == null
        }

        private fun RampAmountState.Data.withConvertedAmount(): RampAmountState.Data {
            val converted = when {
                isCryptoInput -> formatAmount(fiatCode, requestAmount ?: Coins.ZERO)
                else -> selectedProvider?.quote?.receiveAmount ?: run {
                    val estimated = if (isBuy) {
                        cryptoPerFiat?.let { rate -> requestAmount?.multiply(rate) }
                    } else {
                        null
                    }
                    formatAmount(receiveCurrencyCode, estimated ?: Coins.ZERO)
                }
            }
            return if (converted == convertedAmount) { this } else { copy(convertedAmount = converted) }
        }

        private fun parseRate(rate: String): BigDecimal? {
            return rate.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
        }

        private fun convertInputText(
            text: String,
            toFiat: Boolean,
            cryptoPerFiat: BigDecimal,
            fiatScale: Int,
            cryptoDecimals: Int,
        ): String {
            val amount = parseAmount(text)?.value ?: return ""
            val converted = if (toFiat) {
                amount.divide(cryptoPerFiat, fiatScale, RoundingMode.HALF_UP)
            } else {
                val crypto = amount.multiply(cryptoPerFiat)
                crypto.setScale(minOf(CurrencyFormatter.getScale(crypto), cryptoDecimals), RoundingMode.DOWN)
            }
            if (converted.signum() <= 0) {
                return ""
            }
            return converted.stripTrailingZeros().toPlainString().withLocaleDecimalSeparator()
        }
    }
}

private fun formatAmount(code: String, coins: Coins): String {
    return CurrencyFormatter.format(code, coins, replaceSymbol = false).toString()
}
