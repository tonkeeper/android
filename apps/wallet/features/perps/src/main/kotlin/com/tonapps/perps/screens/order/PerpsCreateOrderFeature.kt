package com.tonapps.perps.screens.order

import androidx.annotation.StringRes
import com.tonapps.async.Async
import com.tonapps.chainkit.perpetual.PerpetualReview
import com.tonapps.log.L
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.flow.withLatestFrom
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.mvi.graph.KResult
import com.tonapps.mvi.graph.KState
import com.tonapps.perps.data.MIN_LEVERAGE
import com.tonapps.perps.data.PERPS_QUOTE_INPUT_SCALE
import com.tonapps.perps.data.PerpsAutoCloseDraft
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLivePrices
import com.tonapps.perps.data.PerpsMarketRules
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.PerpsTradingFlags
import com.tonapps.perps.data.PerpsTradingState
import com.tonapps.perps.data.floorToSizeStep
import com.tonapps.perps.data.formatPlainDecimal
import com.tonapps.perps.data.limitFractionDigits
import com.tonapps.perps.data.perpsTicker
import com.tonapps.perps.data.sanitizeAmount
import com.tonapps.perps.trade.PerpsEntryAutoCloseLimits
import com.tonapps.perps.trade.PerpsOpenLeverageStore
import com.tonapps.perps.trade.PerpsPlannerAdapter
import com.tonapps.perps.trade.PerpsProductRules
import com.tonapps.perps.trade.PerpsTradeRequest
import com.tonapps.perps.trade.entryAutoCloseLimits
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.data.maskIf
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.multichain.lighter.PerpetualRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

interface PerpsCreateOrderViewState : MviViewState {
    val mark: StateFlow<BigDecimal?>
    val orderType: StateFlow<PerpsOrderType>
    val limitPrice: StateFlow<BigDecimal?>
    val rules: StateFlow<PerpsMarketRules?>
    val amount: StateFlow<String>
    val inputInSize: StateFlow<Boolean>
    val amountFractionDigits: StateFlow<Int>
    val leverage: StateFlow<Int>
    val autoClose: StateFlow<PerpsAutoCloseDraft?>
    val autoCloseWarning: StateFlow<PerpsAutoCloseIssue?>
    val draftLiquidation: StateFlow<BigDecimal?>
    val draftEntryLimits: StateFlow<PerpsEntryAutoCloseLimits?>
    val autoCloseAvailable: StateFlow<Boolean>
    val refusal: StateFlow<Int?>
    val marginQuote: StateFlow<BigDecimal>
    val sizeQuote: StateFlow<BigDecimal>
    val sizeBase: StateFlow<BigDecimal?>
    val availableFormat: StateFlow<String>
    val issue: StateFlow<PerpsOrderIssue?>
    val canReview: StateFlow<Boolean>
}

sealed interface PerpsCreateOrderAction : MviAction {
    data class SetAmount(val value: String) : PerpsCreateOrderAction
    data object ToggleInputMode : PerpsCreateOrderAction
    data object Max : PerpsCreateOrderAction
    data class SetLeverage(val value: Int) : PerpsCreateOrderAction
    data class SetOrderType(val type: PerpsOrderType) : PerpsCreateOrderAction
    data class SetLimitPrice(val price: BigDecimal) : PerpsCreateOrderAction
    data class SetAutoClose(val draft: PerpsAutoCloseDraft?) : PerpsCreateOrderAction
    data class SetAutoCloseSheetVisible(val visible: Boolean) : PerpsCreateOrderAction
    data object Retry : PerpsCreateOrderAction
    data class ShowRefusal(@StringRes val text: Int) : PerpsCreateOrderAction
}

class PerpsCreateOrderFeature(
    private val marketIndex: Int,
    val symbol: String,
    val side: PerpsOrderSide,
    private val adapter: PerpsPlannerAdapter,
    private val perpetualRepository: PerpetualRepository,
    private val leverageStore: PerpsOpenLeverageStore,
    livePrices: PerpsLivePrices,
    unifiedAccountRepository: UnifiedAccountRepository,
    private val settingsRepository: SettingsRepository,
) : GraphViewModel<PerpsCreateOrderViewState, PerpsCreateOrderAction>(), PerpsCreateOrderViewState {

    private data class TradingContext(
        val walletId: String,
        val isMultichain: Boolean,
        val attempt: Int,
    )

    private data class AmountInput(
        val text: String,
        val inSize: Boolean,
        val fractionDigits: Int,
        val sizeBelowStep: Boolean = false,
        val previous: AmountInput? = null,
        val maxMargin: BigDecimal? = null,
    )

    private data class OrderSizing(
        val leverage: Int,
        val price: BigDecimal?,
        val rules: PerpsMarketRules?,
    )

    private data class OrderGate(
        val isMultichain: Boolean,
        val error: PerpsError?,
        val rules: PerpsMarketRules?,
        val mark: BigDecimal?,
        val effectivePrice: BigDecimal?,
        val limitMissing: Boolean,
        val openBlocked: Boolean,
        val loading: Boolean,
    )

    private data class GatePrice(
        val mark: BigDecimal?,
        val effectivePrice: BigDecimal?,
        val limitMissing: Boolean,
    )

    private data class OrderAmounts(
        val entered: Boolean,
        val margin: BigDecimal,
        val sizeQuote: BigDecimal,
        val available: BigDecimal?,
    )

    private data class DraftContext(
        val walletId: String,
        val input: AmountInput,
        val leverage: Int,
        val limitPrice: BigDecimal?,
        val sheetOpenings: Int,
    ) {
        fun samePlanAs(other: DraftContext): Boolean = this == other.copy(sheetOpenings = sheetOpenings)
    }

    private data class ReviewedDraft(
        val draft: DraftContext,
        val liquidation: PerpsDraftLiquidation,
    )

    private data class MinMarginKey(
        val context: TradingContext,
        val leverage: Int,
        val limitPrice: BigDecimal?,
        val reload: Int,
    )

    private sealed interface AmountCmd {
        data class Set(val value: String) : AmountCmd
        data class Toggle(val sizing: OrderSizing) : AmountCmd
        data class Max(val sizing: OrderSizing, val balance: BigDecimal?) : AmountCmd
        data class Reprice(val sizing: OrderSizing) : AmountCmd
        data object ForgetPrevious : AmountCmd
    }

    private val wallet = unifiedAccountRepository.selectedTonWalletFlow
        .cacheState()

    private val reloadAttempt = actions
        .on<PerpsCreateOrderAction.Retry>()
        .runningFold(0) { attempt, _ -> attempt + 1 }
        .cacheState(initialValue = 0)

    private val context = combine(
        wallet,
        reloadAttempt,
        transform = { wallet, attempt ->
            val walletId = wallet?.id ?: return@combine null
            TradingContext(
                walletId = walletId,
                isMultichain = wallet.supportsPerps,
                attempt = attempt,
            )
        }
    )
        .filterNotNull()
        .distinctUntilChanged()

    private val isMultichain = context
        .map { it.isMultichain }
        .cacheState(initialValue = true)

    private val tradingState = context
        .transformStateLatest { context ->
            if (context.isMultichain) {
                adapter.tradingState(marketIndex, context.walletId)
            } else {
                KResult.Ok<PerpsTradingState, PerpsError>(PerpsTradingState.PUBLIC)
            }
        }
        .cacheStateWithLoading()

    private val trading = tradingState
        .map { it.state?.getOrNull() }
        .cacheState(initialValue = null)

    private val loadError = tradingState
        .mapStateDataValueOrNull()
        .mapResultErrorOrNull()
        .map { it?.value }
        .cacheState(initialValue = null)

    private val flags = trading
        .map { it?.flags ?: PerpsTradingFlags.ALL_ENABLED }
        .cacheState(initialValue = PerpsTradingFlags.ALL_ENABLED)

    private val hasAccount = trading
        .map { it?.hasAccount == true }
        .cacheState(initialValue = false)

    private val tradingLoading = tradingState
        .isLoading()
        .cacheState(initialValue = true)

    private val livePrice = livePrices
        .observe(flowOf(setOf(perpsTicker(symbol))))
        .map { it[perpsTicker(symbol)] }
        .cacheState(initialValue = null)

    override val rules = trading
        .map { it?.rules }
        .cacheState(initialValue = null)

    private val availableBalance = tradingState
        .mapStateDataValueOrNull()
        .map { it?.getOrNull()?.availableBalance }
        .cacheState(initialValue = null)

    override val availableFormat = combine(availableBalance, settingsRepository.hiddenBalancesFlow) { available, _ ->
        maskIf(settingsRepository.hiddenBalances) { formatUsdAmount(available ?: BigDecimal.ZERO) }
    }
        .cacheState(initialValue = maskIf(settingsRepository.hiddenBalances) { formatUsdAmount(BigDecimal.ZERO) })

    override val mark = combine(
        livePrice,
        rules,
        transform = { live, rules -> live ?: rules?.markPrice }
    )
        .cacheState(initialValue = null)

    override val orderType = merge(
        actions
            .on<PerpsCreateOrderAction.SetOrderType>()
            .map { it.type },
        actions
            .on<PerpsCreateOrderAction.SetLimitPrice>()
            .map { PerpsOrderType.Limit },
    )
        .cacheState(initialValue = PerpsOrderType.Market)

    override val limitPrice = merge(
        actions
            .on<PerpsCreateOrderAction.SetLimitPrice>()
            .map { it.price },
        actions
            .on<PerpsCreateOrderAction.SetOrderType>()
            .filter { it.type == PerpsOrderType.Market }
            .map { null },
    )
        .cacheState(initialValue = null)

    private val effectivePrice = combine(
        limitPrice,
        mark,
        transform = { limit, mark -> limit ?: mark }
    )
        .cacheState(initialValue = null)

    private val limitMissing = combine(
        orderType,
        limitPrice,
        transform = { type, limit -> type == PerpsOrderType.Limit && limit == null }
    )
        .cacheState(initialValue = false)

    private val leverageSelection = actions
        .on<PerpsCreateOrderAction.SetLeverage>()
        .map { it.value }
        .cacheState(initialValue = null)

    private val rememberedLeverage = context
        .map { leverageStore.leverage(it.walletId, marketIndex) }
        .flowOn(Async.Io)

    override val leverage = combine(
        leverageSelection,
        rememberedLeverage,
        rules,
        transform = { value, remembered, rules ->
            val chosen = value ?: remembered
            if (rules != null) {
                val leverage = chosen ?: PerpsProductRules.defaultLeverage(rules.maxLeverage)
                leverage.coerceIn(MIN_LEVERAGE, rules.maxLeverage)
            } else {
                chosen ?: PerpsProductRules.DEFAULT_LEVERAGE
            }
        }
    )
        .cacheState(initialValue = PerpsProductRules.DEFAULT_LEVERAGE)

    override val autoCloseAvailable = combine(
        hasAccount,
        flags,
        transform = { hasAccount, flags -> !hasAccount || flags.autoCloseEnabled }
    )
        .cacheState(initialValue = true)

    private val minMarginReload = actions
        .on<PerpsCreateOrderAction.ShowRefusal>()
        .filter { it.text == Localization.perps_review_invalid_amount }
        .runningFold(0) { reloads, _ -> reloads + 1 }
        .cacheState(initialValue = 0)

    private val plannedMinMargin = combine(
        context,
        leverage,
        limitPrice,
        minMarginReload,
        transform = ::MinMarginKey,
    )
        .distinctUntilChanged()
        .transformStateLatest { key ->
            key.context.takeIf { it.isMultichain }?.let { loadMinMargin(it.walletId, key.leverage, key.limitPrice) }
        }
        .cacheStateWithLoading()

    private val minMargin = plannedMinMargin
        .filter { it !is KState.Loading }
        .mapStateDataValueOrNull()
        .map { planned ->
            planned?.setScale(PERPS_QUOTE_INPUT_SCALE, RoundingMode.UP)?.max(MIN_OPEN_MARGIN) ?: MIN_OPEN_MARGIN
        }
        .cacheState(initialValue = MIN_OPEN_MARGIN)

    private val amountInput = merge(
        actions
            .on<PerpsCreateOrderAction.SetAmount>()
            .map { AmountCmd.Set(it.value) },
        actions
            .on<PerpsCreateOrderAction.ToggleInputMode>()
            .withLatestFrom(leverage, effectivePrice, rules) { _, leverage, price, rules ->
                AmountCmd.Toggle(OrderSizing(leverage, price, rules))
            },
        actions
            .on<PerpsCreateOrderAction.Max>()
            .withLatestFrom(leverage, effectivePrice, rules) { _, leverage, price, rules ->
                OrderSizing(leverage, price, rules)
            }
            .withLatestFrom(tradingState) { sizing, state ->
                AmountCmd.Max(
                    sizing = sizing,
                    balance = (state as? KState.Data)?.state?.getOrNull()?.availableBalance,
                )
            },
        actions
            .filter {
                it is PerpsCreateOrderAction.SetLeverage ||
                    it is PerpsCreateOrderAction.SetLimitPrice ||
                    it is PerpsCreateOrderAction.SetOrderType
            }
            .map { AmountCmd.ForgetPrevious },
        combine(leverage, effectivePrice, rules, ::OrderSizing)
            .map { AmountCmd.Reprice(it) },
    )
        .runningFold(DOLLAR_INPUT, ::reduceAmount)
        .cacheState(initialValue = DOLLAR_INPUT)

    override val amount = amountInput
        .map { it.text }
        .cacheState(initialValue = "")

    override val inputInSize = amountInput
        .map { it.inSize }
        .cacheState(initialValue = false)

    override val amountFractionDigits = amountInput
        .map { it.fractionDigits }
        .cacheState(initialValue = PERPS_QUOTE_INPUT_SCALE)

    override val marginQuote = combine(
        amountInput,
        leverage,
        effectivePrice,
        rules,
        transform = ::marginOf,
    )
        .cacheState(initialValue = BigDecimal.ZERO)

    private val lastRefusal = merge(
        actions
            .on<PerpsCreateOrderAction.ShowRefusal>()
            .map { it.text },
        actions
            .filter { it !is PerpsCreateOrderAction.ShowRefusal }
            .map { null },
    )
        .cacheState(initialValue = null)

    override val refusal = combine(
        lastRefusal,
        minMargin,
        marginQuote,
        transform = { text, minMargin, margin ->
            text?.takeUnless { it == Localization.perps_review_invalid_amount && margin < minMargin }
        }
    )
        .cacheState(initialValue = null)

    private val autoCloseSheetVisible = actions
        .on<PerpsCreateOrderAction.SetAutoCloseSheetVisible>()
        .map { it.visible }
        .cacheState(initialValue = false)

    private val autoCloseRequested = actions
        .on<PerpsCreateOrderAction.SetAutoClose>()
        .map { it.draft != null }
        .cacheState(initialValue = false)

    private val autoCloseSheetOpenings = actions
        .on<PerpsCreateOrderAction.SetAutoCloseSheetVisible>()
        .filter { it.visible }
        .runningFold(0) { openings, _ -> openings + 1 }
        .cacheState(initialValue = 0)

    private val draftReviewRound = combine(
        autoCloseSheetVisible,
        autoCloseRequested,
        autoCloseSheetOpenings,
        transform = { visible, requested, openings -> openings.takeIf { visible || requested } }
    )
        .cacheState(initialValue = null)

    private val draftContext = combine(
        context,
        amountInput,
        leverage,
        limitPrice,
        draftReviewRound,
        transform = { context, input, leverage, limitPrice, round ->
            val entered = input.text.toBigDecimalOrNull()
            if (context.isMultichain && entered != null && entered.signum() > 0 && round != null) {
                DraftContext(
                    walletId = context.walletId,
                    input = if (input.maxMargin == null) {
                        input
                    } else {
                        input.copy(text = "")
                    },
                    leverage = leverage,
                    limitPrice = limitPrice,
                    sheetOpenings = round,
                )
            } else {
                null
            }
        }
    )
        .distinctUntilChanged()
        .cacheState(initialValue = null)

    private val draftLiquidationState = draftContext
        .withLatestFrom(mark, rules) { draft, mark, rules ->
            draft?.let { it to marginOf(it.input, it.leverage, it.limitPrice ?: mark, rules) }
        }
        .transformStateLatest { pending ->
            val (draft, margin) = pending ?: return@transformStateLatest null
            delay(DRAFT_REVIEW_DEBOUNCE)
            ReviewedDraft(draft = draft, liquidation = reviewDraft(draft, margin))
        }
        .cacheStateWithLoading()

    private val reviewedDraft = combine(
        draftLiquidationState,
        draftContext,
        transform = { state, draft -> state.state?.takeIf { draft != null && it.draft.samePlanAs(draft) } }
    )
        .cacheState(initialValue = null)

    private val reviewedDraftLiquidation = reviewedDraft
        .map { it?.liquidation }
        .cacheState(initialValue = null)

    override val draftLiquidation = combine(
        combine(
            limitPrice,
            leverage,
            transform = { limit, leverage -> PerpsAutoCloseTerms(limit, leverage) }
        ),
        reviewedDraftLiquidation,
        mark,
        rules,
        transform = { terms, reviewed, mark, rules ->
            resolveAutoCloseLiquidation(terms, reviewed, autoCloseLiquidationEstimate(side, terms, mark, rules)).price
        }
    )
        .cacheState(initialValue = null)

    override val draftEntryLimits = combine(
        reviewedDraftLiquidation,
        limitPrice,
        leverage,
        transform = { liquidation, limit, leverage -> liquidation.entryLimitsFor(PerpsAutoCloseTerms(limit, leverage)) }
    )
        .cacheState(initialValue = null)

    private val autoCloseState = merge(
        actions
            .on<PerpsCreateOrderAction.SetAutoClose>()
            .map { PerpsAutoCloseCmd.Set(it.draft) },
        combine(
            limitPrice,
            leverage,
            reviewedDraftLiquidation,
            transform = { limit, leverage, reviewed -> PerpsAutoCloseTerms(limit, leverage) to reviewed }
        )
            .withLatestFrom(mark, rules) { (terms, reviewed), mark, rules ->
                val estimate = autoCloseLiquidationEstimate(side, terms, mark, rules)
                PerpsAutoCloseCmd.Check(
                    terms = terms,
                    mark = mark,
                    liquidation = resolveAutoCloseLiquidation(terms, reviewed, estimate),
                    priceDecimals = rules?.priceDecimals,
                )
            },
    )
        .runningFold(PerpsAutoCloseEntry(draft = null, warning = null)) { entry, cmd -> entry.reduce(side, cmd) }
        .cacheState(initialValue = PerpsAutoCloseEntry(draft = null, warning = null))

    override val autoClose = combine(
        autoCloseState,
        effectivePrice,
        leverage,
        rules,
        transform = { entry, price, leverage, rules ->
            val draft = entry.draft
            if (draft == null || price == null || rules == null) {
                draft
            } else {
                draft.repriced(price, BigDecimal(leverage), rules.priceDecimals, side)
            }
        }
    )
        .cacheState(initialValue = null)

    private val autoCloseUnplaceable = combine(
        autoCloseState,
        effectivePrice,
        leverage,
        rules,
        transform = { entry, price, leverage, rules ->
            val draft = entry.draft
            draft != null && price != null && rules != null &&
                draft.hasUnplaceableRoi(price, BigDecimal(leverage), rules.priceDecimals, side)
        }
    )
        .cacheState(initialValue = false)

    override val autoCloseWarning = combine(
        autoCloseState,
        autoCloseUnplaceable,
        transform = { entry, unplaceable ->
            if (unplaceable) {
                PerpsAutoCloseIssue(Localization.perps_review_auto_close_invalid)
            } else {
                entry.warning
            }
        }
    )
        .cacheState(initialValue = null)

    override val sizeQuote = combine(
        marginQuote,
        leverage,
        transform = { margin, leverage -> margin.multiply(BigDecimal(leverage)) }
    )
        .cacheState(initialValue = BigDecimal.ZERO)

    override val sizeBase = combine(
        amountInput,
        sizeQuote,
        effectivePrice,
        rules,
        transform = { input, quote, price, rules ->
            when {
                price == null || price.signum() <= 0 || rules == null -> null
                input.inSize -> floorToSizeStep(input.text.toBigDecimalOrNull() ?: BigDecimal.ZERO, rules)
                else -> floorToSizeStep(quote.divide(price, rules.sizeDecimals, RoundingMode.DOWN), rules)
            }
        }
    )
        .cacheState(initialValue = null)

    private val gate = combine(
        isMultichain,
        loadError,
        rules,
        combine(mark, effectivePrice, limitMissing, ::GatePrice),
        combine(
            hasAccount,
            flags,
            tradingLoading,
            transform = { hasAccount, flags, loading -> (hasAccount && !flags.openEnabled) to loading }
        ),
        transform = { isMultichain, error, rules, price, (openBlocked, loading) ->
            OrderGate(
                isMultichain = isMultichain,
                error = error,
                rules = rules,
                mark = price.mark,
                effectivePrice = price.effectivePrice,
                limitMissing = price.limitMissing,
                openBlocked = openBlocked,
                loading = loading,
            )
        }
    )

    private val amounts = combine(
        amountInput,
        marginQuote,
        sizeQuote,
        availableBalance,
        transform = { input, margin, sizeQuote, available ->
            OrderAmounts(
                entered = input.sizeBelowStep || (input.text.toBigDecimalOrNull()?.signum() ?: 0) > 0,
                margin = margin,
                sizeQuote = sizeQuote,
                available = available,
            )
        }
    )

    override val issue = combine(
        gate,
        amounts,
        minMargin,
        transform = { gate, amounts, minMargin ->
            val sizingPrice = gate.effectivePrice ?: gate.mark
            when {
                !gate.isMultichain -> PerpsOrderIssue.TradingDisabled
                gate.error != null -> PerpsOrderIssue.Failed(gate.error)
                gate.loading || gate.rules == null || gate.mark == null -> PerpsOrderIssue.Loading
                sizingPrice == null || sizingPrice.signum() <= 0 -> PerpsOrderIssue.Loading
                gate.openBlocked || !gate.rules.tradable -> PerpsOrderIssue.TradingDisabled
                gate.limitMissing -> PerpsOrderIssue.LimitPriceMissing
                !amounts.entered -> null
                amounts.margin < minMargin -> PerpsOrderIssue.BelowMinMargin(
                    minMargin = minMargin,
                    canDeposit = (amounts.available ?: BigDecimal.ZERO) < minMargin,
                )
                amounts.margin > (amounts.available ?: BigDecimal.ZERO) -> PerpsOrderIssue.InsufficientBalance
                else -> null
            }
        }
    )
        .cacheState(initialValue = PerpsOrderIssue.Loading)

    override val canReview = combine(
        issue,
        marginQuote,
        combine(
            autoCloseUnplaceable,
            plannedMinMargin,
            transform = { autoCloseUnplaceable, minMargin -> autoCloseUnplaceable || minMargin is KState.Loading },
        ),
        transform = { issue, margin, held -> issue == null && margin.signum() > 0 && !held }
    )
        .cacheState(initialValue = false)

    private suspend fun reviewDraft(draft: DraftContext, margin: BigDecimal): PerpsDraftLiquidation {
        val terms = PerpsAutoCloseTerms(limitPrice = draft.limitPrice, leverage = draft.leverage)
        if (margin.signum() <= 0) {
            return PerpsDraftLiquidation(terms = terms, price = null)
        }
        return try {
            withContext(Async.Io) {
                val credentials = perpetualRepository.signingCredentials(draft.walletId)
                    ?: return@withContext PerpsDraftLiquidation(terms = terms, price = null)
                val planned = adapter.plan(
                    request = PerpsTradeRequest.Open(
                        walletId = draft.walletId,
                        marketIndex = marketIndex,
                        side = side,
                        marginQuote = margin,
                        leverage = draft.leverage,
                        autoClose = null,
                        limitPrice = draft.limitPrice,
                    ),
                    credentials = credentials,
                    mark = null,
                    operationId = UUID.randomUUID().toString(),
                ).getOrNull()
                val review = planned?.plan?.review as? PerpetualReview.Open
                PerpsDraftLiquidation(
                    terms = terms,
                    price = review?.liquidationPrice?.let { BigDecimal.valueOf(it, review.scale.priceDecimals) },
                    entryLimits = planned?.entryAutoCloseLimits(),
                )
            }
        } catch (t: Throwable) {
            verifyError(t)
            L.e(t)
            PerpsDraftLiquidation(terms = terms, price = null)
        }
    }

    private fun reduceAmount(input: AmountInput, cmd: AmountCmd): AmountInput = when (cmd) {
        is AmountCmd.Set -> {
            val text = limitFractionDigits(sanitizeAmount(cmd.value), input.fractionDigits)
            if (text == input.text) {
                input
            } else {
                AmountInput(text = text, inSize = input.inSize, fractionDigits = input.fractionDigits)
            }
        }

        is AmountCmd.Toggle -> toggleAmount(input, cmd.sizing)

        is AmountCmd.Max -> maxAmount(input, cmd.sizing, cmd.balance)

        is AmountCmd.Reprice -> maxSized(input, cmd.sizing)

        AmountCmd.ForgetPrevious -> input.copy(previous = null)
    }

    private fun toggleAmount(input: AmountInput, sizing: OrderSizing): AmountInput {
        val price = sizing.price?.takeIf { it.signum() > 0 } ?: return input
        val rules = sizing.rules ?: return input
        val current = input.copy(previous = null)
        input.previous?.let { return maxSized(it, sizing).copy(previous = current) }

        val budget = input.maxMargin
        val entered = input.text.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        val toggled = if (input.inSize) {
            if (budget != null) {
                val cents = budget.setScale(PERPS_QUOTE_INPUT_SCALE, RoundingMode.DOWN)
                DOLLAR_INPUT.copy(text = amountText(cents), maxMargin = cents)
            } else {
                val margin = entered
                    ?.let { marginForSize(floorToSizeStep(it, rules), price, sizing.leverage, rules) }
                    ?.setScale(PERPS_QUOTE_INPUT_SCALE, RoundingMode.DOWN)
                DOLLAR_INPUT.copy(text = amountText(margin))
            }
        } else {
            if (budget != null) {
                maxSized(
                    AmountInput(text = "", inSize = true, fractionDigits = rules.sizeDecimals, maxMargin = budget),
                    sizing,
                )
            } else {
                val size = entered?.let { sizeForMargin(it, price, sizing.leverage, rules) }
                AmountInput(
                    text = size?.let(::formatPlainDecimal).orEmpty(),
                    inSize = true,
                    fractionDigits = rules.sizeDecimals,
                    sizeBelowStep = size?.signum() == 0,
                )
            }
        }
        return toggled.copy(previous = current)
    }

    private fun maxAmount(input: AmountInput, sizing: OrderSizing, balance: BigDecimal?): AmountInput {
        balance ?: return input
        return if (input.inSize) {
            val price = sizing.price
            if (price == null || price.signum() <= 0) {
                return input
            }
            val rules = sizing.rules ?: return input
            maxSized(
                AmountInput(
                    text = "",
                    inSize = true,
                    fractionDigits = input.fractionDigits,
                    maxMargin = balance.setScale(quoteScale(rules), RoundingMode.DOWN),
                ),
                sizing,
            )
        } else {
            val text = limitFractionDigits(formatPlainDecimal(balance), input.fractionDigits)
            AmountInput(
                text = text,
                inSize = false,
                fractionDigits = input.fractionDigits,
                maxMargin = text.toBigDecimalOrNull(),
            )
        }
    }

    private fun maxSized(input: AmountInput, sizing: OrderSizing): AmountInput {
        if (!input.inSize) {
            return input
        }
        val budget = input.maxMargin ?: return input
        val price = sizing.price?.takeIf { it.signum() > 0 } ?: return input
        val rules = sizing.rules ?: return input
        val size = sizeForMargin(budget, price, sizing.leverage, rules)
        return input.copy(
            text = limitFractionDigits(formatPlainDecimal(size), input.fractionDigits),
            sizeBelowStep = size.signum() == 0 && budget.signum() > 0,
        )
    }

    private fun marginOf(input: AmountInput, leverage: Int, price: BigDecimal?, rules: PerpsMarketRules?): BigDecimal {
        input.maxMargin?.let { return it }
        val entered = input.text.toBigDecimalOrNull() ?: return BigDecimal.ZERO
        return when {
            !input.inSize -> entered
            price == null || price.signum() <= 0 -> BigDecimal.ZERO
            else -> marginForSize(rules?.let { floorToSizeStep(entered, it) } ?: entered, price, leverage, rules)
        }
    }

    private fun marginForSize(size: BigDecimal, price: BigDecimal, leverage: Int, rules: PerpsMarketRules?): BigDecimal {
        return size.multiply(price).divide(BigDecimal(leverage), quoteScale(rules), RoundingMode.UP)
    }

    private fun quoteScale(rules: PerpsMarketRules?): Int {
        return rules?.let { it.priceDecimals + it.sizeDecimals } ?: QUOTE_SCALE
    }

    private fun sizeForMargin(margin: BigDecimal, price: BigDecimal, leverage: Int, rules: PerpsMarketRules): BigDecimal {
        val size = margin.multiply(BigDecimal(leverage)).divide(price, rules.sizeDecimals, RoundingMode.DOWN)
        return floorToSizeStep(size, rules)
    }

    private suspend fun loadMinMargin(walletId: String, leverage: Int, limitPrice: BigDecimal?): BigDecimal? {
        return try {
            withContext(Async.Io) {
                adapter.minOpenMargin(
                    PerpsTradeRequest.Open(
                        walletId = walletId,
                        marketIndex = marketIndex,
                        side = side,
                        marginQuote = MIN_OPEN_MARGIN,
                        leverage = leverage,
                        autoClose = null,
                        limitPrice = limitPrice,
                    )
                )
            }
        } catch (t: Throwable) {
            verifyError(t)
            L.e(t)
            null
        }
    }

    private fun amountText(value: BigDecimal?): String {
        return value?.takeIf { it.signum() > 0 }?.let(::formatPlainDecimal).orEmpty()
    }

    private companion object {
        const val QUOTE_SCALE = 6
        val DRAFT_REVIEW_DEBOUNCE = 500.milliseconds
        val DOLLAR_INPUT = AmountInput(text = "", inSize = false, fractionDigits = PERPS_QUOTE_INPUT_SCALE)
        val MIN_OPEN_MARGIN = BigDecimal(PerpsProductRules.MIN_OPEN_MARGIN_QUOTE).setScale(PERPS_QUOTE_INPUT_SCALE)
    }
}

/*
 *   selectedTonWalletFlow ──► wallet? ──┐
 *                                       ├──► [distinct] context ──┬──► isMultichain
 *      Retry ──► [fold] reloadAttempt ──┘                         └──► [latest] tradingState
 *
 *   tradingState ──► trading? ──┬──► rules?
 *                               ├──► flags ─────────┐
 *                               └──► hasAccount ────┴──► autoCloseAvailable
 *   tradingState [dataOrNull, errOrNull] ──► loadError?
 *   tradingState [dataOrNull, okOrNull] ──► availableBalance?
 *   availableBalance? / hiddenBalancesFlow ──► availableFormat
 *   tradingState [loading] ──► tradingLoading
 *
 *                  livePrice? ──┐
 *                      rules? ──┴──► mark?
 *
 *   SetOrderType / SetLimitPrice ──┬──► orderType ──────┐
 *                                  └──► limitPrice? ──┬─┴──► limitMissing
 *                                             mark? ──┴──► effectivePrice?
 *
 *        SetLeverage ──► leverageSelection? ──┐
 *   context ──► [io] rememberedLeverage? ─────┤
 *                                    rules? ──┴──► leverage
 *
 *   ShowRefusal (invalid amount) ──► [fold] minMarginReload
 *   context, leverage, limitPrice?, minMarginReload ──► [distinct, latest] plannedMinMargin
 *   plannedMinMargin ──► [skip loading, dataOrNull] minMargin
 *
 *                                                     SetAmount ──┐
 *         ToggleInputMode, leverage ⋯, effectivePrice? ⋯, rules? ⋯┤
 *   Max, leverage ⋯, effectivePrice? ⋯, rules? ⋯, tradingState ⋯──┤
 *   SetLeverage / SetLimitPrice / SetOrderType ───────────────────┼──► [fold] amountInput ──┬──► amount
 *               leverage, effectivePrice?, rules? ──► Reprice ────┘                         ├──► inputInSize
 *                                                                                           └──► amountFractionDigits
 *
 *       amountInput ──┐
 *          leverage ──┤
 *   effectivePrice? ──┼──► marginQuote ──┬──► sizeQuote ──┐
 *            rules? ──┘     leverage ────┘                ├──► sizeBase?
 *                            amountInput, effectivePrice?, rules? ─┘
 *
 *     ShowRefusal ──┬──► lastRefusal? ──┐
 *   other actions ──┘        minMargin ──┼──► refusal?
 *                          marginQuote ──┘
 *
 *   SetAutoCloseSheetVisible ──► autoCloseSheetVisible ──┐
 *               SetAutoClose ──► autoCloseRequested ─────┼──► draftReviewRound?
 *   SetAutoCloseSheetVisible ──► autoCloseSheetOpenings ─┘
 *
 *              context ──┐
 *          amountInput ──┤
 *             leverage ──┼──► [distinct] draftContext? ──┬──► [debounce, latest] draftLiquidationState
 *          limitPrice? ──┤               mark?, rules? ⋯┘
 *    draftReviewRound? ──┘
 *   draftLiquidationState [kept while loading] ──┐
 *                               draftContext? ──┴──► [same plan] reviewedDraft? ──► reviewedDraftLiquidation?
 *
 *       limitPrice?, leverage ──┐
 *   reviewedDraftLiquidation? ──┼──► [plan or estimate] draftLiquidation?
 *               mark?, rules? ──┘
 *
 *                                        SetAutoClose ──────────┐
 *   limitPrice?, leverage, reviewedDraftLiquidation? ──┐        ├──► [fold] autoCloseState ──┬──► autoClose?
 *          mark? ⋯, rules? ⋯ [plan or estimate] ───────┴────────┘                            └──► autoCloseUnplaceable
 *                                           effectivePrice?, leverage, rules? ───────────────────┘
 *   autoCloseState / autoCloseUnplaceable ──► autoCloseWarning?
 *
 *   isMultichain / loadError? / rules? / mark? / effectivePrice? / limitMissing /
 *   hasAccount / flags / tradingLoading ──► gate ───────────────────────────────────────────────┐
 *                                                                                               │
 *   amountInput / marginQuote / sizeQuote / availableBalance? ──► amounts ──────────────────────┼──► issue?
 *                                                                                   minMargin ──┘
 *
 *                       issue? ──┐
 *                  marginQuote ──┼──► canReview
 *         autoCloseUnplaceable ──┤
 *   plannedMinMargin [loading] ──┘
 */
