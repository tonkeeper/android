package com.tonapps.perps.screens.order

import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.flow.withLatestFrom
import com.tonapps.mvi.graph.GraphViewModel
import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.PerpsLivePrices
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.formatOffsetPercent
import com.tonapps.perps.data.formatPlainDecimal
import com.tonapps.perps.data.formatUsdPrice
import com.tonapps.perps.data.limitFractionDigits
import com.tonapps.perps.data.perpsTicker
import com.tonapps.perps.data.sanitizeAmount
import com.tonapps.perps.trade.PerpsPlannerAdapter
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningFold
import java.math.BigDecimal
import java.math.RoundingMode

data class PerpsLimitInput(
    val text: String,
    val inPercent: Boolean,
    val quickFill: BigDecimal?,
)

interface PerpsLimitPriceViewState : MviViewState {
    val referencePrice: StateFlow<BigDecimal?>
    val priceDecimals: StateFlow<Int>
    val input: StateFlow<PerpsLimitInput>
    val limitPrice: StateFlow<BigDecimal?>
    val chipText: StateFlow<String>
    val warning: StateFlow<Int?>
    val canSet: StateFlow<Boolean>
}

sealed interface PerpsLimitPriceAction : MviAction {
    data class SetInput(val value: String) : PerpsLimitPriceAction
    data object ToggleInputMode : PerpsLimitPriceAction
    data class SelectQuickFill(val percent: BigDecimal) : PerpsLimitPriceAction
}

class PerpsLimitPriceFeature(
    private val marketIndex: Int,
    symbol: String,
    private val side: PerpsOrderSide,
    initialPrice: BigDecimal?,
    private val adapter: PerpsPlannerAdapter,
    livePrices: PerpsLivePrices,
    unifiedAccountRepository: UnifiedAccountRepository,
) : GraphViewModel<PerpsLimitPriceViewState, PerpsLimitPriceAction>(), PerpsLimitPriceViewState {

    private sealed interface LimitCmd {
        data class Set(val text: String, val decimals: Int) : LimitCmd
        data class Toggle(val reference: BigDecimal?, val decimals: Int) : LimitCmd
        data class QuickFill(val percent: BigDecimal, val reference: BigDecimal?, val decimals: Int) : LimitCmd
    }

    val quickFills = QUICK_FILL_PERCENTS.map { percent ->
        if (side == PerpsOrderSide.LONG) {
            percent.negate()
        } else {
            percent
        }
    }

    private val initialInput = PerpsLimitInput(
        text = initialPrice?.let { formatPlainDecimal(it) }.orEmpty(),
        inPercent = false,
        quickFill = null,
    )

    private val rules = unifiedAccountRepository.selectedTonWalletFlow
        .map { it?.id }
        .filterNotNull()
        .distinctUntilChanged()
        .transformStateLatest { walletId -> adapter.tradingState(marketIndex, walletId).getOrNull()?.rules }
        .map { it.state }
        .cacheState(initialValue = null)

    override val priceDecimals = rules
        .map { it?.priceDecimals ?: DEFAULT_PRICE_DECIMALS }
        .cacheState(initialValue = DEFAULT_PRICE_DECIMALS)

    private val livePrice = livePrices
        .observe(flowOf(setOf(perpsTicker(symbol))))
        .map { it[perpsTicker(symbol)] }
        .cacheState(initialValue = null)

    override val referencePrice = combine(
        livePrice,
        rules,
        transform = { live, rules -> live ?: rules?.markPrice }
    )
        .cacheState(initialValue = null)

    override val input = merge(
        actions
            .on<PerpsLimitPriceAction.SetInput>()
            .withLatestFrom(priceDecimals) { action, decimals -> LimitCmd.Set(action.value, decimals) },
        actions
            .on<PerpsLimitPriceAction.ToggleInputMode>()
            .withLatestFrom(referencePrice) { _, reference -> reference }
            .withLatestFrom(priceDecimals) { reference, decimals -> LimitCmd.Toggle(reference, decimals) },
        actions
            .on<PerpsLimitPriceAction.SelectQuickFill>()
            .withLatestFrom(referencePrice) { action, reference -> action.percent to reference }
            .withLatestFrom(priceDecimals) { (percent, reference), decimals ->
                LimitCmd.QuickFill(percent, reference, decimals)
            },
    )
        .runningFold(initialInput, ::reduceInput)
        .cacheState(initialValue = initialInput)

    override val limitPrice = combine(
        input,
        referencePrice,
        priceDecimals,
        transform = { input, reference, decimals -> priceOf(input, reference, decimals) }
    )
        .cacheState(initialValue = null)

    override val chipText = combine(
        input,
        limitPrice,
        referencePrice,
        priceDecimals,
        transform = { input, price, reference, decimals ->
            if (input.inPercent) {
                price?.let { formatUsdPrice(it.toDouble(), decimals) } ?: PERPS_EMPTY_VALUE
            } else {
                offsetPercent(price, reference)?.let { formatOffsetPercent(it) } ?: PERPS_EMPTY_VALUE
            }
        }
    )
        .cacheState(initialValue = PERPS_EMPTY_VALUE)

    override val warning = combine(
        input,
        limitPrice,
        referencePrice,
        transform = { input, price, reference ->
            when {
                price == null || reference == null || input.quickFill?.signum() == 0 -> null
                side == PerpsOrderSide.LONG && price > reference -> Localization.perps_limit_price_warning_below
                side == PerpsOrderSide.SHORT && price < reference -> Localization.perps_limit_price_warning_above
                else -> null
            }
        }
    )
        .cacheState(initialValue = null)

    override val canSet = combine(
        limitPrice,
        warning,
        transform = { price, warning -> price != null && warning == null }
    )
        .cacheState(initialValue = false)

    private fun reduceInput(input: PerpsLimitInput, cmd: LimitCmd): PerpsLimitInput = when (cmd) {
        is LimitCmd.Set -> input.copy(
            text = sanitizeInput(cmd.text, input.inPercent, cmd.decimals),
            quickFill = null,
        )

        is LimitCmd.Toggle -> {
            val price = priceOf(input, cmd.reference, cmd.decimals)
            val text = if (input.inPercent) {
                price?.let { formatPlainDecimal(it) }.orEmpty()
            } else {
                offsetPercent(price, cmd.reference)?.let { percentText(it) }.orEmpty()
            }
            input.copy(text = text, inPercent = !input.inPercent)
        }

        is LimitCmd.QuickFill -> {
            val text = if (input.inPercent) {
                percentText(cmd.percent)
            } else {
                cmd.reference
                    ?.let { formatPlainDecimal(applyPercent(it, cmd.percent, cmd.decimals)) }
                    .orEmpty()
            }
            input.copy(text = text, quickFill = cmd.percent)
        }
    }

    private fun priceOf(input: PerpsLimitInput, reference: BigDecimal?, decimals: Int): BigDecimal? {
        val entered = input.text.toBigDecimalOrNull() ?: return null
        val price = if (input.inPercent) {
            applyPercent(reference ?: return null, entered, decimals)
        } else {
            entered.setScale(decimals, RoundingMode.HALF_UP)
        }

        return price.takeIf { it.signum() > 0 }
    }

    private fun applyPercent(reference: BigDecimal, percent: BigDecimal, decimals: Int): BigDecimal {
        return reference
            .multiply(HUNDRED.add(percent))
            .divide(HUNDRED, decimals, RoundingMode.HALF_UP)
    }

    private fun offsetPercent(price: BigDecimal?, reference: BigDecimal?): BigDecimal? {
        if (price == null || reference == null || reference.signum() <= 0) {
            return null
        }
        return price.subtract(reference)
            .multiply(HUNDRED)
            .divide(reference, PERCENT_SCALE, RoundingMode.HALF_UP)
    }

    private fun percentText(value: BigDecimal): String {
        val plain = formatPlainDecimal(value)
        if (value.signum() > 0) {
            return "$PLUS$plain"
        }

        return plain
    }

    private fun sanitizeInput(text: String, inPercent: Boolean, decimals: Int): String {
        if (!inPercent) {
            return limitFractionDigits(sanitizeAmount(text), decimals)
        }
        val sign = when {
            text.startsWith(MINUS) || text.startsWith(ASCII_MINUS) -> ASCII_MINUS
            text.startsWith(PLUS) -> PLUS
            else -> ""
        }

        return "$sign${limitFractionDigits(sanitizeAmount(text), PERCENT_SCALE)}"
    }

    private companion object {
        const val DEFAULT_PRICE_DECIMALS = 2
        const val PERCENT_SCALE = 2
        const val MINUS = "−"
        const val ASCII_MINUS = "-"
        const val PLUS = "+"
        val HUNDRED: BigDecimal = BigDecimal(100)
        val QUICK_FILL_PERCENTS = listOf(0, 1, 2, 5).map { BigDecimal(it) }
    }
}

/*
 *   selectedTonWalletFlow ──► [distinct, latest] rules? ──┬──► priceDecimals
 *                                                         │
 *                                           livePrice? ──┬┴──► referencePrice?
 *
 *                             SetInput, priceDecimals ⋯ ─┐
 *   ToggleInputMode, referencePrice? ⋯, priceDecimals ⋯ ─┼──► [fold] input
 *   SelectQuickFill, referencePrice? ⋯, priceDecimals ⋯ ─┘
 *
 *                      input ──┐
 *           referencePrice? ──┼──► limitPrice? ──┐
 *             priceDecimals ──┘                  │
 *   input / referencePrice? ─────────────────────┴──► warning? ──┬──► canSet
 *                                        limitPrice? ────────────┘
 *
 *   input / limitPrice? / referencePrice? / priceDecimals ──► chipText
 */
