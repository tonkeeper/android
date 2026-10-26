package com.tonapps.perps.screens.margin

import androidx.annotation.StringRes
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.flow.withLatestFrom
import com.tonapps.perps.data.PERPS_QUOTE_INPUT_SCALE
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLivePrices
import com.tonapps.perps.data.PerpsMarketRules
import com.tonapps.perps.data.PerpsOpenPosition
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.data.formatPlainDecimal
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.data.formatUsdPrice
import com.tonapps.perps.data.limitFractionDigits
import com.tonapps.perps.data.maskIf
import com.tonapps.perps.data.perpsPositionLiquidation
import com.tonapps.perps.data.sanitizeAmount
import com.tonapps.perps.screens.common.PerpsPositionFormFeature
import com.tonapps.perps.screens.common.PerpsPositionFormGate
import com.tonapps.perps.trade.PerpsPlannerAdapter
import com.tonapps.perps.trade.PerpsPositionBounds
import com.tonapps.perps.trade.PerpsProductRules
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.take
import java.math.BigDecimal
import java.math.RoundingMode

interface PerpsMarginChangeViewState : MviViewState {
    val position: StateFlow<PerpsOpenPosition?>
    val mark: StateFlow<BigDecimal?>
    val priceDecimals: StateFlow<Int>
    val amount: StateFlow<String>
    val availableFormat: StateFlow<String>
    val liquidation: StateFlow<PerpsLiquidationChange?>
    val refusal: StateFlow<Int?>
    val issue: StateFlow<PerpsMarginChangeIssue?>
    val canReview: StateFlow<Boolean>
}

sealed interface PerpsMarginChangeAction : MviAction {
    data class SetAmount(val value: String) : PerpsMarginChangeAction
    data object Max : PerpsMarginChangeAction
    data object Retry : PerpsMarginChangeAction
    data class ShowRefusal(@StringRes val text: Int) : PerpsMarginChangeAction
}

data class PerpsLiquidationChange(
    val current: String?,
    val next: String?,
    val atRisk: Boolean,
)

sealed interface PerpsMarginChangeIssue {
    data object Loading : PerpsMarginChangeIssue
    data class Failed(val error: PerpsError) : PerpsMarginChangeIssue
    data object NoPosition : PerpsMarginChangeIssue
    data object TradingDisabled : PerpsMarginChangeIssue
    data class MinAmount(val min: BigDecimal) : PerpsMarginChangeIssue
    data class ExceedsMargin(val maxFormat: String) : PerpsMarginChangeIssue
    data object InsufficientBalance : PerpsMarginChangeIssue
    data object ImmediateRisk : PerpsMarginChangeIssue
}

class PerpsMarginChangeFeature(
    marketIndex: Int,
    symbol: String,
    val side: PerpsOrderSide,
    val add: Boolean,
    repository: PerpsRepository,
    adapter: PerpsPlannerAdapter,
    livePrices: PerpsLivePrices,
    unifiedAccountRepository: UnifiedAccountRepository,
    settingsRepository: SettingsRepository,
) : PerpsPositionFormFeature<PerpsMarginChangeViewState, PerpsMarginChangeAction>(
    marketIndex = marketIndex,
    symbol = symbol,
    repository = repository,
    adapter = adapter,
    livePrices = livePrices,
    unifiedAccountRepository = unifiedAccountRepository,
    settingsRepository = settingsRepository,
    isRetry = { it is PerpsMarginChangeAction.Retry },
    isEnabled = { flags ->
        if (add) {
            flags.addMarginEnabled
        } else {
            flags.removeMarginEnabled
        }
    },
),
    PerpsMarginChangeViewState {

    private data class MarginAmounts(
        val amount: BigDecimal,
        val available: BigDecimal?,
        val bounds: PerpsPositionBounds?,
        val boundsUnavailable: Boolean,
        val atRisk: Boolean,
    )

    private sealed interface AmountCmd {
        data class Set(val value: String) : AmountCmd
        data class Max(val amount: BigDecimal?) : AmountCmd
    }

    private val marginBounds: StateFlow<PerpsPositionBounds?> = if (add) {
        bounds
    } else {
        bounds.filterNotNull().take(1).cacheState(initialValue = null)
    }

    private val amountInput = merge(
        actions
            .on<PerpsMarginChangeAction.SetAmount>()
            .map { AmountCmd.Set(it.value) },
        actions
            .on<PerpsMarginChangeAction.Max>()
            .withLatestFrom(availableBalance) { _, balance -> balance }
            .withLatestFrom(marginBounds) { balance, bounds ->
                if (add) {
                    AmountCmd.Max(bounds?.maxAddMarginQuote ?: balance)
                } else {
                    AmountCmd.Max(bounds?.maxRemoveMarginQuote)
                }
            },
    )
        .runningFold("", ::reduceAmount)
        .cacheState(initialValue = "")

    override val amount = amountInput
        .cacheState(initialValue = "")

    private val amountQuote = amount
        .map { it.toBigDecimalOrNull() ?: BigDecimal.ZERO }
        .cacheState(initialValue = BigDecimal.ZERO)

    override val liquidation = combine(
        position,
        amountQuote,
        mark,
        rules,
        transform = { position, amount, mark, rules -> liquidation(position, amount, mark, rules) }
    )
        .cacheState(initialValue = null)

    private val available = combine(
        availableBalance,
        marginBounds,
        transform = { balance, bounds ->
            if (add) {
                (bounds?.maxAddMarginQuote ?: balance)?.setScale(PERPS_QUOTE_INPUT_SCALE, RoundingMode.DOWN)
            } else {
                bounds?.maxRemoveMarginQuote
            }
        }
    )
        .cacheState(initialValue = null)

    override val availableFormat = available.toAvailableFormat()

    private val amounts = combine(
        amountQuote,
        availableBalance,
        marginBounds,
        boundsUnavailable,
        liquidation,
        transform = { amount, available, bounds, boundsUnavailable, liquidation ->
            MarginAmounts(
                amount = amount,
                available = available,
                bounds = bounds,
                boundsUnavailable = boundsUnavailable,
                atRisk = liquidation?.atRisk == true,
            )
        }
    )

    override val issue = combine(
        gate,
        amounts,
        settingsRepository.hiddenBalancesFlow,
        transform = { gate, amounts, _ -> issue(gate, amounts) }
    )
        .cacheState(initialValue = PerpsMarginChangeIssue.Loading)

    override val refusal = merge(
        actions
            .on<PerpsMarginChangeAction.ShowRefusal>()
            .map { it.text },
        actions
            .filter { it !is PerpsMarginChangeAction.ShowRefusal }
            .map { null },
    )
        .cacheState(initialValue = null)

    override val canReview = combine(
        issue,
        amountQuote,
        transform = { issue, amount -> issue == null && amount.signum() > 0 }
    )
        .cacheState(initialValue = false)

    private fun issue(gate: PerpsPositionFormGate, amounts: MarginAmounts): PerpsMarginChangeIssue? {
        val amount = amounts.amount
        val margin = gate.detail?.position?.margin
        val minimum = amounts.bounds?.minAdjustMarginQuote ?: MIN_ADJUST_MARGIN
        val maximum = amounts.bounds?.maxRemoveMarginQuote

        return when {
            !gate.isMultichain -> PerpsMarginChangeIssue.TradingDisabled
            gate.error != null -> PerpsMarginChangeIssue.Failed(gate.error)
            gate.loading -> PerpsMarginChangeIssue.Loading
            gate.detail == null -> PerpsMarginChangeIssue.NoPosition
            !gate.enabled -> PerpsMarginChangeIssue.TradingDisabled
            !add && maximum == null && amounts.boundsUnavailable -> PerpsMarginChangeIssue.Failed(PerpsError.Unknown)
            !add && maximum == null -> PerpsMarginChangeIssue.Loading
            amount.signum() <= 0 -> null
            !add && maximum != null && maximum < minimum -> exceedsMargin(maximum)
            amount < minimum -> PerpsMarginChangeIssue.MinAmount(minimum)
            add -> {
                val ceiling = amounts.bounds?.maxAddMarginQuote
                    ?: amounts.available
                    ?: BigDecimal.ZERO
                if (amount > ceiling) {
                    PerpsMarginChangeIssue.InsufficientBalance
                } else {
                    null
                }
            }

            margin != null && amount >= margin -> exceedsMargin(maximum ?: margin)

            maximum != null && amount > maximum -> exceedsMargin(maximum)
            amounts.atRisk -> PerpsMarginChangeIssue.ImmediateRisk
            else -> null
        }
    }

    private fun exceedsMargin(max: BigDecimal): PerpsMarginChangeIssue {
        return PerpsMarginChangeIssue.ExceedsMargin(maskIf(settingsRepository.hiddenBalances) { formatUsdAmount(max) })
    }

    private fun liquidation(
        position: PerpsOpenPosition?,
        amount: BigDecimal,
        mark: BigDecimal?,
        rules: PerpsMarketRules?,
    ): PerpsLiquidationChange? {
        val decimals = rules?.priceDecimals ?: DEFAULT_PRICE_DECIMALS
        val current = position?.liquidationPrice?.let { formatUsdPrice(it.toDouble(), decimals) }
        val size = position?.size
        val entry = position?.avgEntryPrice
        val margin = position?.margin
        val maintenance = rules?.maintenanceMarginBps
        if (position == null || size == null || entry == null || margin == null ||
            mark == null || maintenance == null || amount.signum() <= 0
        ) {
            return PerpsLiquidationChange(current = current, next = null, atRisk = false)
        }
        val collateral = if (add) {
            margin.add(amount)
        } else {
            margin.subtract(amount)
        }
        val preview = perpsPositionLiquidation(
            side = position.side,
            size = size,
            entryPrice = entry,
            markPrice = mark,
            maintenanceMarginBps = maintenance,
            collateral = collateral,
        )

        return PerpsLiquidationChange(
            current = current,
            next = preview.price?.let { formatUsdPrice(it.toDouble(), decimals) },
            atRisk = preview.isImmediateRisk,
        )
    }

    private fun reduceAmount(input: String, cmd: AmountCmd): String = when (cmd) {
        is AmountCmd.Set -> limitFractionDigits(sanitizeAmount(cmd.value), PERPS_QUOTE_INPUT_SCALE)

        is AmountCmd.Max -> {
            val amount = cmd.amount
            if (amount != null) {
                limitFractionDigits(formatPlainDecimal(amount), PERPS_QUOTE_INPUT_SCALE)
            } else {
                input
            }
        }
    }

    private companion object {
        const val DEFAULT_PRICE_DECIMALS = 2

        val MIN_ADJUST_MARGIN = BigDecimal(PerpsProductRules.MIN_ADJUST_MARGIN_QUOTE)
    }
}

/*
 *   PerpsPositionFormFeature ──► availableBalance? / rules? / detail? / position? / mark? / bounds? / boundsUnavailable / gate
 *
 *   bounds? ──► [add: live | reduce: first non-null] marginBounds?
 *
 *                                  SetAmount ──┐
 *   Max, availableBalance? ⋯, marginBounds? ⋯──┴──► [fold] amountInput ──► amount ──► amountQuote
 *
 *                   position? ──┐
 *                 amountQuote ──┤
 *                       mark? ──┼──► liquidation?
 *                      rules? ──┘
 *
 *   availableBalance? / marginBounds? ──► available? ──► availableFormat
 *
 *                                                                 gate ──┐
 *                                                                        ├──► issue?
 *   amountQuote / availableBalance? / marginBounds? / boundsUnavailable / liquidation? ──► amounts ──┤
 *                                                      hiddenBalancesFlow ──┘
 *
 *                       issue? ──┐
 *                  amountQuote ──┴──► canReview
 */
