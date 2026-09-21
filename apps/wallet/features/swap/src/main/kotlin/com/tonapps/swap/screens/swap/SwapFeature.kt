package com.tonapps.swap.screens.swap

import android.content.Context
import com.tonapps.blockchain.model.CommonTransactionData
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.num.BaseUnit
import com.tonapps.chainkit.core.chain.model.num.toDisplayUnit
import com.tonapps.core.components.AmountInputConverter
import com.tonapps.core.components.amountText
import com.tonapps.core.components.sanitizeAmountInput
import com.tonapps.core.components.toAssetEntity
import com.tonapps.core.flags.WalletFeature
import com.tonapps.log.L
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.flow.flatMapLatestCatching
import com.tonapps.mvi.flow.mapLatestCatching
import com.tonapps.swap.SwapRoutes
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.exchange.SwapConfig
import com.tonapps.wallet.data.multichain.exchange.SwapDefaultPair
import com.tonapps.wallet.data.multichain.exchange.SwapQuote
import com.tonapps.wallet.data.multichain.exchange.SwapQuoteDirection
import com.tonapps.wallet.data.multichain.exchange.SwapRepository
import com.tonapps.wallet.data.multichain.exchange.SwapSlippage
import com.tonapps.wallet.data.raffle.RaffleRepository
import com.tonapps.wallet.data.raffle.entities.RaffleEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.OffsetDateTime

private const val QUOTE_REQUEST_DELAY_MS = 500L
private const val QUOTE_INACTIVITY_INTERVAL_MS = 30_000L
private const val QUOTE_COUNTDOWN_TICK_MS = 250L
private const val FORMAT_DECIMALS = 8

sealed interface SwapEvent {
    data class ShowError(val message: String) : SwapEvent
    data class Continue(val request: ConfirmRequest) : SwapEvent
}

sealed interface SwapInitState {
    data object Loading : SwapInitState
    data object Error : SwapInitState
    data object Ready : SwapInitState
}

data class DisplayQuote(
    val quote: SwapQuote?,
    val sellAmount: String,
    val buyAmount: String,
    val rateLabel: String,
)

enum class AmountInputSide {
    Sell,
    Buy,
}

private val AmountInputSide.quoteDirection: SwapQuoteDirection
    get() = when (this) {
        AmountInputSide.Sell -> SwapQuoteDirection.ExactInput
        AmountInputSide.Buy -> SwapQuoteDirection.ExactOutput
    }

data class AmountInput(
    val text: String = "",
    val inFiat: Boolean = false,
    val isMax: Boolean = false,
    val side: AmountInputSide = AmountInputSide.Sell,
)

private data class QuoteInput(
    val sell: AccountWithDetails?,
    val buy: AccountWithDetails?,
    val amount: BaseUnit?,
    val direction: SwapQuoteDirection,
    val slippageBps: Int?,
)

// Everything the screen needs before it can show a pair: resolved sides plus the slippage options
// they were fetched with. [SwapFeature.sellAsset]/[SwapFeature.buyAsset] hang off this.
private sealed interface ConfigState {
    data object Loading : ConfigState
    data object Error : ConfigState

    data class Ready(
        val sell: AssetEntity,
        val buy: AssetEntity,
        val config: SwapConfig,
    ) : ConfigState
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SwapFeature(
    private val initialRoute: SwapRoutes.Swap,
    private val context: Context,
    private val oldAccount: AccountRepository,
    private val accountRepo: McAccountRepository,
    private val swapRepo: SwapRepository,
    private val raffleRepository: RaffleRepository,
) : AsyncViewModel() {

    private val relay = MviRelay<SwapEvent>()
    val events = relay.events

    val isReceiveAmountEditable: Boolean = WalletFeature.SwapExactOutput.isEnabled

    // Raffle perk line under the top-bar title while the offer's zero-fee
    // window is active; tapping the title opens that raffle. Hidden once the
    // user has migrated (joined), per the design annotation.
    val zeroFeeRaffleId: StateFlow<String?> = oldAccount.selectedWalletFlow
        .flatMapLatestCatching { wallet ->
            raffleRepository.getRafflesFlow(wallet.id, forced = false)
                .map { raffles ->
                    raffles.firstOrNull { raffle ->
                        raffle.status == RaffleEntity.Status.NotJoined &&
                            raffle.progress?.zeroFeeEndsAt?.isAfter(OffsetDateTime.now()) == true
                    }?.id
                }
        }
        .cacheState(initialValue = null)

    private val reload = MutableStateFlow(0)

    private val config: StateFlow<ConfigState> = reload
        .flatMapLatest {
            flow {
                emit(ConfigState.Loading)
                emit(loadConfig())
            }
        }
        .cacheState(initialValue = ConfigState.Loading)

    // TODO remove, selected if we don't have selected wallet we should close screen
    // TODO initial state should we attached to sellAsset OR buyAsset null value
    val initState: StateFlow<SwapInitState> = config
        .map { state ->
            when (state) {
                is ConfigState.Loading -> SwapInitState.Loading
                is ConfigState.Error -> SwapInitState.Error
                is ConfigState.Ready -> SwapInitState.Ready
            }
        }
        .cacheState(initialValue = SwapInitState.Loading)

    private val selectedSellAsset = MutableStateFlow<AssetEntity?>(null)
    private val selectedBuyAsset = MutableStateFlow<AssetEntity?>(null)

    val sellAsset: StateFlow<AssetEntity?> = combine(config, selectedSellAsset)
        { config, selected -> selected ?: (config as? ConfigState.Ready)?.sell }
        .cacheState(initialValue = null)

    val buyAsset: StateFlow<AssetEntity?> = combine(config, selectedBuyAsset)
        { config, selected -> selected ?: (config as? ConfigState.Ready)?.buy }
        .cacheState(initialValue = null)

    // --- Accounts (nullable: populated when the wallet has this chain account) ---
    // TODO add account cache
    private val sellAccountLoading = MutableStateFlow(false)
    val sellAccount = sellAsset
        .filterNotNull()
        .mapLatestCatching(onFinally = { sellAccountLoading.tryEmit(false) }) {
            sellAccountLoading.tryEmit(true)
            resolveAccount(it)
        }
        .cacheState()

    private val butAccountLoading = MutableStateFlow(false)
    val buyAccount = buyAsset
        .filterNotNull()
        .mapLatestCatching(onFinally = { butAccountLoading.tryEmit(false) }) {
            butAccountLoading.tryEmit(true)
            resolveAccount(it)
        }
        .cacheState()

    // --- Slippage (per sell-chain config; the selected value feeds the quote request) ---
    val slippage: StateFlow<SwapSlippage?> = combine(config, sellAsset)
        { config, asset ->
            val ready = config as? ConfigState.Ready ?: return@combine null
            asset?.valueOrNull?.let { ready.config.slippage(it.chain) }
        }
        .cacheState(initialValue = null)

    // Selected slippage in bps; null means "use the server default" (e.g. chain without config).
    val selectedSlippageBps: StateFlow<Int?> field = MutableStateFlow(null)

    // --- Amount ---
    // isMax is sticky only while the amount MAX produced stays untouched: any manual edit, asset
    // change or side swap makes it stale, so every writer clears it.
    val amountInput: StateFlow<AmountInput> field = MutableStateFlow(AmountInput())

    init {
        bgScope.launch {
            slippage.collect { selectedSlippageBps.tryEmit(it?.defaultBps) }
        }

        bgScope.launch {
            swapRepo.swapCompleted.collect { resetAmount() }
        }

        bgScope.launch {
            sellAccount.collect { account ->
                if (amountInput.value.inFiat && account?.rate == null) {
                    amountInput.update { it.copy(inFiat = false, isMax = false) }
                }
            }
        }
    }

    // --- Quote (2s debounce on input change; recalculated after 30s of inactivity) ---
    private val quoteIdle = MutableStateFlow(false)
    private val quoteLoading = MutableStateFlow(false)
    val quoteCountdown: StateFlow<Float> field = MutableStateFlow(0f)

    val quote: StateFlow<DisplayQuote?> = combine(sellAccount, buyAccount, amountInput, selectedSlippageBps)
        { sell, buy, input, slippageBps ->
            QuoteInput(sell, buy, fixedAmount(sell, buy, input), input.side.quoteDirection, slippageBps)
        }
        .onEach { input ->
            if (input.sell != null && input.buy != null && input.amount != null) {
                quoteIdle.tryEmit(true)
            }
        }
        .debounce(QUOTE_REQUEST_DELAY_MS)
        .flatMapLatest { (sell, buy, amount, direction, slippageBps) ->
            flow {
                quoteIdle.tryEmit(false)

                if (sell == null || buy == null || amount == null) {
                    emit(null)
                    quoteLoading.tryEmit(false)
                    quoteCountdown.tryEmit(0f)
                    return@flow
                }

                quoteLoading.tryEmit(true)

                while (true) {
                    try {
                        emit(fetchQuote(sell, buy, amount, direction, slippageBps))
                    } catch (t: Throwable) {
                        verifyError(t)
                        L.e(t)
                        emit(null)
                    }

                    quoteLoading.tryEmit(false)

                    val totalTicks = (QUOTE_INACTIVITY_INTERVAL_MS / QUOTE_COUNTDOWN_TICK_MS).toInt()
                    for (i in 0..totalTicks) {
                        quoteCountdown.tryEmit(1f - i.toFloat() / totalTicks)
                        if (i < totalTicks) {
                            delay(QUOTE_COUNTDOWN_TICK_MS)
                        }
                    }

                    quoteLoading.tryEmit(true)
                }
            }
        }
        .cacheState()

    val isLoading = combine(quoteLoading, quoteIdle, sellAccountLoading, butAccountLoading)
        { q, w, e, r -> q || w || e || r }
        .cacheState(initialValue = false)

    // --- Validated amounts (for fiat formatting) ---
    val sellValidatedAmount: StateFlow<BaseUnit?> = combine(sellAccount, amountInput, quote)
        { account, input, quote ->
            when (input.side) {
                AmountInputSide.Sell -> parsePositiveAmount(account, input.text, input.inFiat)
                AmountInputSide.Buy -> quote?.quote?.let { account?.asset?.value?.decimals?.baseUnit(it.sourceBaseAmount) }
            }
        }
        .cacheState()

    val buyValidatedAmount: StateFlow<BaseUnit?> = combine(buyAccount, amountInput, quote)
        { account, input, quote ->
            when (input.side) {
                AmountInputSide.Sell -> parsePositiveAmount(account, quote?.buyAmount.orEmpty())
                AmountInputSide.Buy -> parsePositiveAmount(account, input.text, input.inFiat)
            }
        }
        .cacheState()

    val insufficientFunds: StateFlow<Boolean> = combine(sellAccount, sellValidatedAmount)
        { account, amount -> account != null && amount != null && amount > account.unitBalance }
        .cacheState(initialValue = false)

    val continueLoading: StateFlow<Boolean> field = MutableStateFlow(false)
    val continueEnabled: StateFlow<Boolean> = combine(quote, isLoading, continueLoading, insufficientFunds)
        { quote, loading, preparing, insufficient -> quote?.quote != null && !loading && !preparing && !insufficient }
        .cacheState(initialValue = false)

    // --- Public ---
    fun retry() {
        reload.tryEmit(reload.value + 1)
    }

    fun setSellAmount(value: String) {
        amountInput.update {
            it.copy(text = value.sanitizeAmountInput(), isMax = false, side = AmountInputSide.Sell)
        }
    }

    fun setBuyAmount(value: String) {
        if (!isReceiveAmountEditable) {
            return
        }

        amountInput.update {
            it.copy(text = value.sanitizeAmountInput(), isMax = false, side = AmountInputSide.Buy)
        }
    }

    fun selectSlippage(bps: Int) {
        selectedSlippageBps.tryEmit(bps)
    }

    // Picking the asset that already sits on the other side swaps them, so the two never collide.
    fun selectSellAsset(asset: AssetEntity) {
        val flipped = asset.id == buyAsset.value?.id
        amountInput.update {
            if (flipped) {
                it.copy(isMax = false, side = AmountInputSide.Sell)
            } else {
                it.copy(isMax = false)
            }
        }
        if (flipped) {
            selectedBuyAsset.tryEmit(sellAsset.value)
        }

        selectedSellAsset.tryEmit(asset)
    }

    fun selectBuyAsset(asset: AssetEntity) {
        if (asset.id == sellAsset.value?.id) {
            amountInput.update { it.copy(isMax = false, side = AmountInputSide.Sell) }
            selectedSellAsset.tryEmit(buyAsset.value)
        }

        selectedBuyAsset.tryEmit(asset)
    }

    fun toggleInputCurrency() {
        val current = amountInput.value
        val account = when (current.side) {
            AmountInputSide.Sell -> sellAccount.value
            AmountInputSide.Buy -> buyAccount.value
        } ?: return

        val converter = AmountInputConverter(account, FORMAT_DECIMALS)
        if (!converter.hasRate) {
            return
        }

        val toFiat = !current.inFiat
        amountInput.tryEmit(
            AmountInput(text = converter.swapText(current.text, toFiat), inFiat = toFiat, side = current.side),
        )
    }

    fun swapSides() {
        val sell = sellAsset.value ?: return
        val buy = buyAsset.value ?: return

        amountInput.tryEmit(inputCarriedToSellSide())

        selectedSellAsset.tryEmit(buy)
        selectedBuyAsset.tryEmit(sell)
    }

    fun setMax() {
        val account = sellAccount.value ?: return
        // Keep a chain-specific reserve aside (e.g. 0.5 TON) so the swap still leaves enough to
        // cover the network fee; the input shows balance - reserve.
        val reserve = swapRepo.getMaxReserve(account.asset.value)
        val max = account.unitBalance - reserve
        if (!max.isPositive) {
            return
        }

        val converter = AmountInputConverter(account, FORMAT_DECIMALS)
        var inFiat = amountInput.value.inFiat
        var text = converter.maxText(max, inFiat) ?: return

        if (inFiat && converter.positiveTokenUnit(text, inFiat = true) == null) {
            inFiat = false
            text = converter.maxText(max, false) ?: return
        }

        amountInput.tryEmit(AmountInput(text = text, inFiat = inFiat, isMax = true))
    }

    fun onContinue() {
        val currentQuote = quote.value?.quote ?: return
        val sell = sellAccount.value ?: return
        val buy = buyAccount.value ?: return

        if (continueLoading.value || isLoading.value) {
            return
        }

        continueLoading.tryEmit(true)

        bgScope.launch {
            try {
                val walletId = oldAccount.getSelectedWalletId()
                    ?: throw IllegalStateException("No selected wallet")

                val request = ConfirmRequest(
                    data = CommonTransactionData(
                        assetId = sell.asset.value.id,
                        walletId = walletId,
                    ),
                    type = ConfirmType.Swap(
                        sourceAmount = currentQuote.sourceBaseAmount,
                        destinationAssetId = buy.asset.value.id,
                        isMax = amountInput.value.isMax,
                        slippageBps = selectedSlippageBps.value,
                        // Hand the confirm screen the quote we already have so it can render
                        // immediately and only re-request once its countdown expires.
                        quote = ConfirmType.Swap.Quote(
                            routeId = currentQuote.routeId,
                            buyAmount = currentQuote.buyBaseAmount,
                            minBuyAmount = currentQuote.minimumBuyBaseAmount,
                            providerTxId = currentQuote.providerTxId,
                            slippageBps = currentQuote.slippageBps,
                            priceImpactBps = currentQuote.priceImpactBps,
                            provider = currentQuote.provider.id,
                        ),
                    ),
                )
                relay.emit(SwapEvent.Continue(request))
            } catch (t: Throwable) {
                verifyError(t)
                L.e(t)
                relay.emit(SwapEvent.ShowError(t.message ?: context.getString(Localization.swap_error_prepare_failed)))
            } finally {
                continueLoading.tryEmit(false)
            }
        }
    }

    private suspend fun loadConfig(): ConfigState {
        val walletId = oldAccount.getSelectedWalletId()
            ?: return ConfigState.Error

        val config = try {
            swapRepo.getConfig(
                walletId = walletId,
                fromAssetId = initialRoute.sellAssetId,
                toAssetId = initialRoute.buyAssetId,
            )
        } catch (t: Throwable) {
            verifyError(t)
            L.e(t)
            FallbackConfig
        }

        val sell = config.defaultPair.source
            ?: return ConfigState.Error

        val buy = config.defaultPair.destination
            ?: return ConfigState.Error

        return ConfigState.Ready(sell = sell, buy = buy, config = config)
    }

    private fun resetAmount() {
        amountInput.update { it.copy(text = "", isMax = false, side = AmountInputSide.Sell) }
    }

    private suspend fun loadAccount(assetId: String): AccountWithDetails? {
        val walletId = oldAccount.getSelectedWalletId() ?: return null
        return runCatching { accountRepo.findAccount(walletId, assetId) }
            .getOrNull()
    }

    private suspend fun resolveAccount(asset: AssetEntity): AccountWithDetails? {
        val resolved = loadAccount(asset.id) ?: return null
        val image = asset.imageUrl
        if (image.isBlank() || resolved.asset.imageUrl == image) {
            return resolved
        }

        return resolved.copy(asset = resolved.asset.copy(imageUrl = image))
    }

    private fun parsePositiveAmount(
        account: AccountWithDetails?,
        input: String,
        inFiat: Boolean = false,
    ): BaseUnit? {
        if (account == null || input.isBlank()) {
            return null
        }

        return AmountInputConverter(account, FORMAT_DECIMALS)
            .positiveTokenUnit(input, inFiat)
    }

    private fun inputCarriedToSellSide(): AmountInput {
        val input = amountInput.value
        if (input.side == AmountInputSide.Buy) {
            return AmountInput(text = input.text, inFiat = input.inFiat)
        }

        val hasReceiveAmount = !isLoading.value &&
            input.text.isNotBlank() &&
            buyValidatedAmount.value != null

        val received = if (hasReceiveAmount) {
            quote.value?.buyAmount.orEmpty()
        } else {
            ""
        }

        return AmountInput(text = received)
    }

    private fun fixedAmount(
        sell: AccountWithDetails?,
        buy: AccountWithDetails?,
        input: AmountInput,
    ): BaseUnit? {
        val account = when (input.side) {
            AmountInputSide.Sell -> sell
            AmountInputSide.Buy -> buy
        }

        return parsePositiveAmount(account, input.text, input.inFiat)
    }

    private suspend fun fetchQuote(
        sell: AccountWithDetails,
        buy: AccountWithDetails,
        amount: BaseUnit,
        direction: SwapQuoteDirection,
        slippageBps: Int?,
    ): DisplayQuote {
        val quote = swapRepo.fetchQuote(sell, buy, amount.value, slippageBps, direction)
            ?: return DisplayQuote(quote = null, sellAmount = "", buyAmount = "", rateLabel = "")

        val sellDisplay = sell.asset.value.decimals.baseUnit(quote.sourceBaseAmount).toDisplayUnit()
        val buyDisplay = buy.asset.value.decimals.baseUnit(quote.buyBaseAmount).toDisplayUnit()

        return DisplayQuote(
            quote = quote,
            sellAmount = sellDisplay.amountText(MAX_DISPLAY_SCALE),
            buyAmount = buyDisplay.amountText(MAX_DISPLAY_SCALE),
            rateLabel = swapRepo.formatRateLabel(quote, sell.asset.value, buy.asset.value).orEmpty(),
        )
    }

    private companion object {
        const val MAX_DISPLAY_SCALE = 8

        // Both sides are native coins, so they carry their assets without the `/asset` endpoint.
        private val FallbackConfig = SwapConfig(
            defaultPair = SwapDefaultPair(
                source = Chain.Ethereum.Mainnet.toAssetEntity(),
                destination = Chain.Ton.Mainnet.toAssetEntity(),
            ),
            slippagePerChain = emptyMap(),
        )
    }
}
