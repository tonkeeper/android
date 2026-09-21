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
import com.tonapps.extensions.anyOfFlows
import com.tonapps.extensions.noneOfFlows
import com.tonapps.log.L
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.flow.countdown
import com.tonapps.mvi.flow.flatMapLatestCatching
import com.tonapps.mvi.flow.withLatestFrom
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.mvi.graph.KState
import com.tonapps.swap.SwapRoutes
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import com.tonapps.wallet.data.multichain.exchange.SwapConfig
import com.tonapps.wallet.data.multichain.exchange.SwapDefaultPair
import com.tonapps.wallet.data.multichain.exchange.SwapQuote
import com.tonapps.wallet.data.multichain.exchange.SwapRepository
import com.tonapps.wallet.data.multichain.exchange.SwapSlippage
import com.tonapps.wallet.data.raffle.RaffleRepository
import com.tonapps.wallet.data.raffle.entities.RaffleEntity
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import java.time.OffsetDateTime
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

interface GraphSwapState : MviViewState {
    val initState: StateFlow<SwapInitState>
    val zeroFeeRaffleId: StateFlow<String?>

    val sellAsset: StateFlow<AssetEntity?>
    val buyAsset: StateFlow<AssetEntity?>
    val sellAccount: StateFlow<AccountWithDetails?>
    val buyAccount: StateFlow<AccountWithDetails?>

    val amountInput: StateFlow<AmountInput>
    val sellValidatedAmount: StateFlow<BaseUnit?>
    val buyValidatedAmount: StateFlow<BaseUnit?>
    val insufficientFunds: StateFlow<Boolean>

    val slippage: StateFlow<SwapSlippage?>
    val selectedSlippageBps: StateFlow<Int?>

    val quote: StateFlow<DisplayQuote?>
    val quoteTimer: StateFlow<Float?>
    val isLoading: StateFlow<Boolean>

    val continueEnabled: StateFlow<Boolean>
    val continueLoading: StateFlow<Boolean>
}

sealed interface GraphSwapAction : MviAction {

    sealed interface Amount : GraphSwapAction {
        data class Set(val value: String) : Amount
        data object Max : Amount
        data object Toggle : Amount
    }

    sealed interface Asset : GraphSwapAction {
        data class SelectSell(val value: AssetEntity) : Asset
        data class SelectBuy(val value: AssetEntity) : Asset
        data object Swap : Asset
    }

    data class SelectSlippage(val bps: Int) : GraphSwapAction
    data object Requote : GraphSwapAction
    data object Retry : GraphSwapAction
    data object Continue : GraphSwapAction
}

class GraphSwapFeature(
    private val initialRoute: SwapRoutes.Swap,
    private val context: Context,
    private val oldAccount: AccountRepository,
    private val accountRepo: McAccountRepository,
    private val swapRepo: SwapRepository,
    private val raffleRepository: RaffleRepository,
) : GraphViewModel<GraphSwapState, GraphSwapAction>(), GraphSwapState {

    private val relay = MviRelay<SwapEvent>()
    val events = relay.events

    // Raffle perk line under the top-bar title while the offer's zero-fee
    // window is active; tapping the title opens that raffle. Hidden once the
    // user has migrated (joined), per the design annotation.
    override val zeroFeeRaffleId: StateFlow<String?> = oldAccount.selectedWalletFlow
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

    // Config: Retry re-runs the loader, a null payload marks the config as failed.
    private data class ReadyConfig(
        val sell: AssetEntity,
        val buy: AssetEntity,
        val config: SwapConfig,
    )

    private val retryAttempt = actions
        .on<GraphSwapAction.Retry>()
        .runningFold(0) { attempt, _ -> attempt + 1 }
        .cacheState(initialValue = 0)

    private val configState = retryAttempt
        .transformStateLatest { loadConfig() }
        .cacheStateWithLoading()

    override val initState = configState
        .map { state ->
            when {
                state is KState.Loading -> SwapInitState.Loading
                state.state == null -> SwapInitState.Error
                else -> SwapInitState.Ready
            }
        }
        .cacheState(initialValue = SwapInitState.Loading)

    // Assets: sell/buy are one node because selections interact — picking the asset that already
    // sits on the other side swaps them, so the two never collide. Config only fills the sides
    // that have no user selection yet.
    private data class AssetPair(val sell: AssetEntity?, val buy: AssetEntity?)

    // Commands of the assets node: UI actions arrive wrapped in [Action]; [Defaults] is an
    // internal edge — config fills the sides that have no user selection yet.
    private sealed interface AssetCmd {
        data class Action(val value: GraphSwapAction.Asset) : AssetCmd
        data class Defaults(val sell: AssetEntity, val buy: AssetEntity) : AssetCmd
    }

    private val assetPair = merge(
        configState
            .filterAndMapStateData()
            .filterNotNull()
            .map { AssetCmd.Defaults(sell = it.sell, buy = it.buy) },
        actions
            .on<GraphSwapAction.Asset>()
            .map { AssetCmd.Action(it) },
    )
        .runningFold(AssetPair(sell = null, buy = null), ::reduceAssets)
        .cacheState(initialValue = AssetPair(sell = null, buy = null))

    override val sellAsset = assetPair
        .map { it.sell }
        .cacheState(initialValue = null)

    override val buyAsset = assetPair
        .map { it.buy }
        .cacheState(initialValue = null)

    // Accounts (nullable: populated when the wallet has this chain account). KState.Loading keeps
    // the previous account, so the screen shows stale values while the next one resolves.
    private val sellAccountState = sellAsset
        .filterNotNull()
        .transformStateLatest { resolveAccount(it) }
        .cacheStateWithLoading()

    private val sellAccountLoading = sellAccountState.isLoading()

    override val sellAccount = sellAccountState
        .map { it.state }
        .cacheState(initialValue = null)

    private val buyAccountState = buyAsset
        .filterNotNull()
        .transformStateLatest { resolveAccount(it) }
        .cacheStateWithLoading()

    private val buyAccountLoading = buyAccountState.isLoading()

    override val buyAccount = buyAccountState
        .map { it.state }
        .cacheState(initialValue = null)

    // Amount: isMax is sticky only while the amount MAX produced stays untouched — a manual edit
    // or a sell-asset change makes it stale, so every such edge clears it. The sampled sell
    // account feeds the token/fiat converter.

    // Commands of the amount node: UI actions arrive wrapped in [Action], the rest are internal
    // edges merged from repo/graph flows, never sent by the UI.
    private sealed interface AmountCmd {
        data class Action(val value: GraphSwapAction.Amount) : AmountCmd
        data object Reset : AmountCmd
        data object ClearMax : AmountCmd
        data object SyncRate : AmountCmd
    }

    override val amountInput = merge(
        actions
            .on<GraphSwapAction.Amount>()
            .map { AmountCmd.Action(it) },
        swapRepo.swapCompleted
            .map { AmountCmd.Reset },
        sellAsset
            .distinctUntilChangedBy { it?.id }
            .map { AmountCmd.ClearMax },
        sellAccount
            .map { AmountCmd.SyncRate },
    )
        .withLatestFrom(sellAccount) { cmd, account -> cmd to account }
        .runningFold(AmountInput()) { input, (cmd, account) -> reduceAmount(input, cmd, account) }
        .cacheState(initialValue = AmountInput())

    // Slippage (per sell-chain config; the selected value feeds the quote request). null means
    // "use the server default"; a slippage change (chain switch) resets the choice back to it.
    override val slippage = combine(
        configState
            .mapStateDataValueOrNull(),
        sellAsset,
        transform = { config, asset ->
            val ready = config ?: return@combine null
            asset?.valueOrNull?.let { ready.config.slippage(it.chain) }
        }
    )
        .cacheState(initialValue = null)

    override val selectedSlippageBps = merge(
        slippage
            .map { it?.defaultBps },
        actions
            .on<GraphSwapAction.SelectSlippage>()
            .map { it.bps },
    )
        .cacheState(initialValue = null)

    // Quote: any input change re-enters the loader, whose leading delay doubles as the debounce
    // (Loading is emitted before it, so the spinner covers the debounce window too). Requote
    // arrives from the countdown below through the action bus.
    private data class QuoteContext(
        val sell: AccountWithDetails,
        val buy: AccountWithDetails,
        val amount: String,
        val inFiat: Boolean,
        val slippageBps: Int?,
        val attempt: Int,
    )

    private val requoteAttempt = actions
        .on<GraphSwapAction.Requote>()
        .runningFold(0) { attempt, _ -> attempt + 1 }
        .cacheState(initialValue = 0)

    private val quoteState = combine(
        sellAccount,
        buyAccount,
        amountInput,
        selectedSlippageBps,
        requoteAttempt,
        transform = { sell, buy, input, slippageBps, attempt ->
            val sell = sell ?: return@combine null
            val buy = buy ?: return@combine null
            if (input.text.isBlank()) {
                return@combine null
            }

            QuoteContext(sell, buy, input.text, input.inFiat, slippageBps, attempt)
        }
    )
        .distinctUntilChanged()
        .transformStateLatest { ctx ->
            val ctx = ctx
                ?: return@transformStateLatest null

            delay(QUOTE_DEBOUNCE)
            fetchQuote(ctx)
        }
        .cacheStateWithLoading()

    private val quoteLoading = quoteState.isLoading()

    override val quote = quoteState
        .mapStateDataValueOrNull()
        .cacheState(initialValue = null)

    override val isLoading = anyOfFlows(quoteLoading, sellAccountLoading, buyAccountLoading)
        .cacheState(initialValue = false)

    // Validated amounts (for fiat formatting)
    override val sellValidatedAmount = combine(sellAccount, amountInput)
        { account, input -> parsePositiveAmount(account, input.text, input.inFiat) }
        .cacheState()

    override val buyValidatedAmount = combine(buyAccount, quote)
        { account, quote -> parsePositiveAmount(account, quote?.buyAmount.orEmpty()) }
        .cacheState()

    override val insufficientFunds = combine(sellAccount, sellValidatedAmount)
        { account, amount -> account != null && amount != null && amount > account.unitBalance }
        .cacheState(initialValue = false)

    // Continue: samples everything the confirm screen needs; transformStateFirst drops re-taps
    // while the request is being prepared.
    private data class ContinueContext(
        val quote: SwapQuote,
        val sell: AccountWithDetails,
        val buy: AccountWithDetails,
        val input: AmountInput,
        val slippageBps: Int?,
    )

    private val continueContext = combine(
        quote,
        sellAccount,
        buyAccount,
        amountInput,
        selectedSlippageBps,
        transform = { quote, sell, buy, input, slippageBps ->
            val route = quote?.quote ?: return@combine null
            val sell = sell ?: return@combine null
            val buy = buy ?: return@combine null

            ContinueContext(route, sell, buy, input, slippageBps)
        }
    )
        .cacheState(initialValue = null)

    private val continueState = actions
        .on<GraphSwapAction.Continue>()
        .withLatestFrom(continueContext) { _, ctx -> ctx }
        .transformStateFirst { ctx ->
            val ctx = ctx
                ?: return@transformStateFirst null

            runCatching { buildConfirmRequest(ctx) }
                .fold(
                    onSuccess = { relay.emit(SwapEvent.Continue(it)) },
                    onFailure = {
                        verifyError(it)
                        L.e(it)
                        relay.emit(
                            SwapEvent.ShowError(
                                it.message ?: context.getString(Localization.swap_error_prepare_failed)
                            )
                        )
                    }
                )
        }
        .cacheStateWithEmpty()

    override val continueLoading = continueState
        .isLoading()
        .cacheState(initialValue = false)

    override val continueEnabled = combine(
        noneOfFlows(isLoading, continueLoading, insufficientFunds),
        quote,
        transform = { idle, quote -> idle && quote?.quote != null }
    )
        .cacheState(initialValue = false)

    // Timer: ticks only while a real route is on screen; paused while the confirm request is
    // being prepared, so a requote can't race the continue.
    private val quoteCountdown = quote
        .map { it?.quote }
        .countdown(QUOTE_TTL, running = noneOfFlows(continueLoading))
        .onEach { countdown ->
            if (countdown != null && countdown.finished) {
                sendAction(GraphSwapAction.Requote)
            }
        }
        .cacheState(initialValue = null)

    override val quoteTimer = quoteCountdown
        .map { it?.progress }
        .cacheState(initialValue = null)

    // Loaders
    private suspend fun loadConfig(): ReadyConfig? {
        val walletId = oldAccount.getSelectedWalletId()
            ?: return null

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
            ?: return null

        val buy = config.defaultPair.destination
            ?: return null

        return ReadyConfig(sell = sell, buy = buy, config = config)
    }

    private suspend fun resolveAccount(asset: AssetEntity): AccountWithDetails? {
        val walletId = oldAccount.getSelectedWalletId() ?: return null
        val resolved = runCatching { accountRepo.findAccount(walletId, asset.id) }
            .getOrNull()
            ?: return null

        val image = asset.imageUrl
        if (image.isBlank() || resolved.asset.imageUrl == image) {
            return resolved
        }

        return resolved.copy(asset = resolved.asset.copy(imageUrl = image))
    }

    private suspend fun fetchQuote(ctx: QuoteContext): DisplayQuote? {
        val sellBase = parsePositiveAmount(ctx.sell, ctx.amount, ctx.inFiat) ?: return null
        val quote = try {
            swapRepo.fetchQuote(ctx.sell, ctx.buy, sellBase.value, ctx.slippageBps)
                ?: return DisplayQuote(quote = null, sellAmount = "", buyAmount = "", rateLabel = "")
        } catch (t: Throwable) {
            verifyError(t)
            L.e(t)
            return null
        }

        val sellDisplay = ctx.sell.asset.value.decimals.baseUnit(quote.sourceBaseAmount).toDisplayUnit()
        val buyBase = ctx.buy.asset.value.decimals.baseUnit(quote.buyBaseAmount)
        val buyDisplay = buyBase.toDisplayUnit()

        return DisplayQuote(
            quote = quote,
            sellAmount = sellDisplay.amountText(MAX_DISPLAY_SCALE),
            buyAmount = buyDisplay.amountText(MAX_DISPLAY_SCALE),
            rateLabel = swapRepo.formatRateLabel(quote, ctx.sell.asset.value, ctx.buy.asset.value).orEmpty(),
        )
    }

    private suspend fun buildConfirmRequest(ctx: ContinueContext): ConfirmRequest {
        val walletId = oldAccount.getSelectedWalletId()
            ?: throw IllegalStateException("No selected wallet")

        return ConfirmRequest(
            data = CommonTransactionData(
                assetId = ctx.sell.asset.value.id,
                walletId = walletId,
            ),
            type = ConfirmType.Swap(
                sourceAmount = ctx.quote.sourceBaseAmount,
                destinationAssetId = ctx.buy.asset.value.id,
                isMax = ctx.input.isMax,
                slippageBps = ctx.slippageBps,
                // Hand the confirm screen the quote we already have so it can render
                // immediately and only re-request once its countdown expires.
                quote = ConfirmType.Swap.Quote(
                    routeId = ctx.quote.routeId,
                    buyAmount = ctx.quote.buyBaseAmount,
                    minBuyAmount = ctx.quote.minimumBuyBaseAmount,
                    providerTxId = ctx.quote.providerTxId,
                    slippageBps = ctx.quote.slippageBps,
                    priceImpactBps = ctx.quote.priceImpactBps,
                    provider = ctx.quote.provider.id,
                ),
            ),
        )
    }

    // Reducers
    private fun reduceAssets(pair: AssetPair, cmd: AssetCmd): AssetPair = when (cmd) {
        is AssetCmd.Defaults -> AssetPair(
            sell = pair.sell ?: cmd.sell,
            buy = pair.buy ?: cmd.buy,
        )

        is AssetCmd.Action -> when (val action = cmd.value) {
            is GraphSwapAction.Asset.SelectSell -> when (action.value.id) {
                pair.buy?.id -> AssetPair(sell = action.value, buy = pair.sell)
                else -> AssetPair(sell = action.value, buy = pair.buy)
            }

            is GraphSwapAction.Asset.SelectBuy -> when (action.value.id) {
                pair.sell?.id -> AssetPair(sell = pair.buy, buy = action.value)
                else -> AssetPair(sell = pair.sell, buy = action.value)
            }

            is GraphSwapAction.Asset.Swap -> AssetPair(sell = pair.buy, buy = pair.sell)
        }
    }

    private fun reduceAmount(
        input: AmountInput,
        cmd: AmountCmd,
        account: AccountWithDetails?,
    ): AmountInput = when (cmd) {
        is AmountCmd.Action -> when (val action = cmd.value) {
            is GraphSwapAction.Amount.Set ->
                input.copy(text = action.value.sanitizeAmountInput(), isMax = false)
            is GraphSwapAction.Amount.Toggle -> toggleCurrency(input, account)
            is GraphSwapAction.Amount.Max -> maxAmount(input, account)
        }

        is AmountCmd.Reset -> input.copy(text = "", isMax = false)

        is AmountCmd.ClearMax -> input.copy(isMax = false)

        // Fiat input needs a rate: fall back to token input when the account loses it.
        is AmountCmd.SyncRate -> if (input.inFiat && account?.rate == null) {
            input.copy(inFiat = false, isMax = false)
        } else {
            input
        }
    }

    private fun toggleCurrency(input: AmountInput, account: AccountWithDetails?): AmountInput {
        val converter = AmountInputConverter(account ?: return input, FORMAT_DECIMALS)
        if (!converter.hasRate) {
            return input
        }

        val toFiat = !input.inFiat
        return AmountInput(text = converter.swapText(input.text, toFiat), inFiat = toFiat)
    }

    private fun maxAmount(input: AmountInput, account: AccountWithDetails?): AmountInput {
        account ?: return input

        // Keep a chain-specific reserve aside (e.g. 0.5 TON) so the swap still leaves enough to
        // cover the network fee; the input shows balance - reserve.
        val reserve = swapRepo.getMaxReserve(account.asset.value)
        val max = account.unitBalance - reserve
        if (!max.isPositive) {
            return input
        }

        val converter = AmountInputConverter(account, FORMAT_DECIMALS)
        var inFiat = input.inFiat
        var text = converter.maxText(max, inFiat) ?: return input

        if (inFiat && converter.positiveTokenUnit(text, inFiat = true) == null) {
            inFiat = false
            text = converter.maxText(max, false) ?: return input
        }

        return AmountInput(text = text, inFiat = inFiat, isMax = true)
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

    private companion object {
        const val FORMAT_DECIMALS = 8
        const val MAX_DISPLAY_SCALE = 8

        private val QUOTE_DEBOUNCE = 500.milliseconds
        private val QUOTE_TTL = 30.seconds

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

/*
 *            Retry ──► retryAttempt ──► configState ──► initState
 *
 *                        configState ──┐
 *   Asset.SelectSell / Asset.SelectBuy ┼──► assetPair ──┬──► sellAsset?
 *                        Asset.Swap ───┘                └──► buyAsset?
 *
 *       sellAsset? ──► sellAccountState ──┬──► sellAccountLoading
 *                                         └──► sellAccount?
 *
 *        buyAsset? ──► buyAccountState ──┬──► buyAccountLoading
 *                                        └──► buyAccount?
 *
 *   Amount.Set / Amount.Max / Amount.Toggle ──┐
 *                   swapRepo.swapCompleted ───┤
 *                   sellAsset? (id change) ───┼──► amountInput
 *                              sellAccount? ──┤
 *                              buyAccount? ⋯─┘
 *
 *                      configState ──┐
 *                                    ├──► slippage? ──┐
 *                       sellAsset? ──┘                ├──► selectedSlippageBps?
 *                                   SelectSlippage ───┘
 *
 *          Requote ──► requoteAttempt ──┐
 *                         sellAccount? ─┤
 *                          buyAccount? ─┼──► quoteState ──┬──► quoteLoading
 *                          amountInput ─┤                 └──► quote?
 *                 selectedSlippageBps? ─┘
 *
 *                         sellAccount? ─┬──► sellValidatedAmount? ─┐
 *                          amountInput ─┘              │           ├──► insufficientFunds
 *                                        sellAccount? ─┴───────────┘
 *
 *                          buyAccount? ─┐
 *                               quote? ─┴──► buyValidatedAmount?
 *
 *   quote? / sellAccount? / buyAccount? / amountInput / selectedSlippageBps? ──► continueContext?
 *
 *                             Continue ──┐
 *                     continueContext? ⋯─┴──► continueState? ──┬──► continueLoading
 *                                                              └──► events
 *
 *   quoteLoading / sellAccountLoading / buyAccountLoading ──► isLoading
 *
 *    isLoading / continueLoading / insufficientFunds ──┐
 *                                                      ├──► continueEnabled
 *                                              quote? ─┘
 *
 *                               quote? ──┐
 *                                        ├──► quoteCountdown? ──► quoteTimer?
 *            continueLoading (pauses) ───┘            ╎
 *                                                     ╎
 *                              Requote ◄╌╌╌╌╌╌╌╌╌╌╌╌╌╌┘
 *
 *        selectedWalletFlow ──► zeroFeeRaffleId?
 */
