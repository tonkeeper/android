package com.tonapps.deposit.multicoin.screens.ramp.amount

import com.tonapps.bus.generated.Events.DepositFlow.DepositFlowWalletMode
import com.tonapps.deposit.data.ExchangeRepository
import com.tonapps.deposit.multicoin.analytics.RampAnalytics
import com.tonapps.deposit.multicoin.data.RampQuote
import com.tonapps.deposit.multicoin.data.RampRepository
import com.tonapps.deposit.screens.provider.ProviderItem
import com.tonapps.deposit.screens.provider.ProviderQuote
import com.tonapps.deposit.screens.provider.ProviderRate
import com.tonapps.deposit.screens.provider.ProviderWithQuote
import com.tonapps.deposit.screens.ramp.RampType
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.log.L
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import io.exchangeapi.models.ExchangePaymentMethodType
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.math.BigDecimal

sealed interface RampAmountState {
    data object Loading : RampAmountState

    data class Data(
        val rampType: RampType,
        val symbol: String,
        val imageUrl: String,
        val paymentMethodType: ExchangePaymentMethodType,
        val fiatCode: String,
        val cryptoCode: String,
        val address: String,
        val balanceCoins: Coins? = null,
        val balanceFormatted: String? = null,
        val providers: List<ProviderWithQuote> = emptyList(),
        val selectedProviderId: String? = null,
        val isCalculating: Boolean = false,
        val selectedErrorProvider: ProviderItem? = null,
        val defaultReceiveAmount: String,
        val initialAmount: String? = null,
    ) : RampAmountState {

        val isBuy: Boolean get() = rampType == RampType.RampOn

        // buy = input fiat, sell = input crypto
        val inputCurrencyCode: String get() = if (isBuy) { fiatCode } else { cryptoCode }
        val receiveCurrencyCode: String get() = if (isBuy) { cryptoCode } else { fiatCode }

        val selectedProvider: ProviderWithQuote?
            get() = providers.find { it.info.id == selectedProviderId }

        val canContinue: Boolean
            get() = !isCalculating && address.isNotBlank() && selectedProvider?.quote != null
    }
}

sealed interface RampAmountEvent {
    data class Continue(val widgetUrl: String) : RampAmountEvent
}

@OptIn(FlowPreview::class)
class RampAmountFeature(
    private val data: RampAmountData,
    private val rampRepository: RampRepository,
    private val exchangeRepository: ExchangeRepository,
    private val accountRepository: AccountRepository,
    private val mcAccountRepository: McAccountRepository,
    private val settingsRepository: SettingsRepository,
) : AsyncViewModel() {

    val state: StateFlow<RampAmountState> field = MutableStateFlow<RampAmountState>(RampAmountState.Loading)

    val events: SharedFlow<RampAmountEvent> field = MutableSharedFlow(extraBufferCapacity = 1)

    private val amountInput = MutableSharedFlow<String>(extraBufferCapacity = 1)

    private var quotesByMerchant: Map<String, RampQuote> = emptyMap()
    private var walletId: String? = null

    private val analytics = RampAnalytics.of(data.rampType, data.analyticsFrom, DepositFlowWalletMode.Multi, data.utm)
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
        amountInput.tryEmit(amount)
    }

    fun selectProvider(providerId: String) {
        state.postExactData { copy(selectedProviderId = providerId) }
    }

    fun continueWithProvider() {
        bgScope.launch {
            val current = state.value as? RampAmountState.Data ?: return@launch
            val provider = current.selectedProvider ?: return@launch
            val quote = provider.quote ?: return@launch
            analytics.clickRampInsertAmountContinue(
                cryptoAsset = data.assetId,
                fiatCode = current.fiatCode,
                paymentMethod = current.paymentMethodType.value,
                providerName = provider.info.title,
                amount = quote.amount.value.toDouble(),
            )
            createOrder(provider.info.id)
        }
    }

    private fun load() {
        bgScope.launch {
            try {
                val wallet = accountRepository.getSelectedWalletId() ?: return@launch
                walletId = wallet

                val assetDetail = rampRepository.getAsset(data.rampType, data.assetId, fiat = data.fiat)
                val method = assetDetail.methods.firstOrNull { it.type.value == data.paymentMethodType }
                    ?: assetDetail.methods.firstOrNull()
                    ?: throw IllegalStateException("No payment method for ${data.assetId}")

                val requestedFiat = data.fiat ?: settingsRepository.currency.code
                val fiatCode = requestedFiat.takeIf { requested -> method.providers.any { it.fiat == requested } }
                    ?: method.providers.firstOrNull()?.fiat
                    ?: requestedFiat
                val fiatProviders = method.providers.filter { it.fiat == fiatCode }

                val account = mcAccountRepository.findAccount(wallet, data.assetId)
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
                        ?.stripTrailingZeros()
                        ?.toPlainString()
                } else {
                    null
                }

                val merchants = runCatching { exchangeRepository.getMerchants() }.getOrDefault(emptyList())
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
                        address = address,
                        balanceCoins = balanceCoins,
                        balanceFormatted = balanceFormatted,
                        providers = providers,
                        defaultReceiveAmount = defaultAmount(
                            if (data.rampType == RampType.RampOn) { assetDetail.symbol } else { fiatCode }
                        ),
                        initialAmount = initialAmount,
                    )
                )
            } catch (e: Throwable) {
                L.e(e)
                state.tryEmit(RampAmountState.Loading)
            }
        }
    }

    private suspend fun onAmountChanged(amountText: String) {
        val current = state.value as? RampAmountState.Data ?: return

        val amount = runCatching {
            val coins = Coins.of(amountText)
            if (coins.isPositive) { coins } else { null }
        }.getOrNull()

        if (amount == null) {
            quotesByMerchant = emptyMap()
            state.postExactData {
                copy(
                    isCalculating = false,
                    selectedErrorProvider = null,
                    providers = providers.map { it.copy(rate = null, quote = null) },
                )
            }
            return
        }

        state.postExactData { copy(isCalculating = true, selectedErrorProvider = null) }

        try {
            val quotes = rampRepository.getQuotes(
                rampType = current.rampType,
                assetId = data.assetId,
                fiat = current.fiatCode,
                amount = amountText,
                paymentMethod = current.paymentMethodType,
            )

            val itemsMap = quotes.items.associateBy { it.merchant.value }
            quotesByMerchant = itemsMap
            val suggestedMap = quotes.suggested.associateBy { it.merchant.value }
            val bestId = quotes.items.firstOrNull()?.merchant?.value

            state.postExactData {
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
                                receiveAmount = defaultAmount(receiveCurrencyCode, receiveCoins),
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

                val selected = updated.firstOrNull { it.info.isBest && it.quote != null }
                    ?: updated.firstOrNull { it.quote != null }

                val errorProvider = if (selected == null) {
                    val suggestedEntry = quotes.suggested.firstOrNull { it.minAmount != null }
                        ?: quotes.suggested.firstOrNull { it.maxAmount != null }
                    suggestedEntry?.let { entry ->
                        (updated.find { it.info.id == entry.merchant.value }?.info ?: providers.firstOrNull()?.info)
                            ?.copy(
                                minAmount = entry.minAmount?.let { Coins.of(it) } ?: Coins.ZERO,
                                maxAmount = entry.maxAmount?.let { Coins.of(it) },
                            )
                    } ?: providers.firstOrNull()?.info
                } else {
                    null
                }

                copy(
                    providers = updated,
                    selectedProviderId = selected?.info?.id,
                    isCalculating = false,
                    selectedErrorProvider = errorProvider,
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
                    providers = providers.map { it.copy(rate = null, quote = null) },
                )
            }
        }
    }

    private suspend fun createOrder(providerId: String) {
        val current = state.value as? RampAmountState.Data ?: return
        val quote = quotesByMerchant[providerId] ?: return
        val provider = current.providers.find { it.info.id == providerId } ?: return
        val providerQuote = provider.quote ?: return
        if (current.address.isBlank()) {
            return
        }

        try {
            val widgetUrl = rampRepository.createOrder(
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
            events.tryEmit(RampAmountEvent.Continue(widgetUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            L.e(e)
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
        return "1 ${data.inputCurrencyCode} ≈ ${defaultAmount(data.receiveCurrencyCode, perUnit)}"
    }

    private fun defaultAmount(code: String, coin: Coins = Coins.ZERO): String {
        return CurrencyFormatter.format(code, coin, replaceSymbol = false).toString()
    }

    private inline fun MutableStateFlow<RampAmountState>.postExactData(
        crossinline setter: RampAmountState.Data.() -> RampAmountState.Data,
    ) {
        val current = value as? RampAmountState.Data ?: return
        tryEmit(setter(current))
    }
}
