package com.tonapps.perps.screens.sizechange

import androidx.annotation.StringRes
import com.tonapps.mvi.contract.MviAction
import com.tonapps.mvi.contract.MviViewState
import com.tonapps.mvi.flow.withLatestFrom
import com.tonapps.perps.data.PERPS_QUOTE_INPUT_SCALE
import com.tonapps.perps.data.PerpsAutoCloseDraft
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLivePrices
import com.tonapps.perps.data.PerpsOpenPosition
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.PerpsRepository
import com.tonapps.perps.data.PerpsSizeDirection
import com.tonapps.perps.data.autoCloseLeverage
import com.tonapps.perps.data.formatPlainDecimal
import com.tonapps.perps.data.formatTokenAmount
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.data.legsDraft
import com.tonapps.perps.data.limitFractionDigits
import com.tonapps.perps.data.maskIf
import com.tonapps.perps.data.sanitizeAmount
import com.tonapps.perps.screens.common.PerpsPositionFormFeature
import com.tonapps.perps.screens.common.PerpsPositionFormGate
import com.tonapps.perps.screens.order.PerpsAutoCloseEstimate
import com.tonapps.perps.screens.order.autoCloseEstimate
import com.tonapps.perps.screens.order.repriced
import com.tonapps.perps.trade.PerpsAutoCloseEdit
import com.tonapps.perps.trade.PerpsPositionBounds
import com.tonapps.perps.trade.PerpsPlannerAdapter
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningFold
import java.math.BigDecimal
import java.math.RoundingMode

interface PerpsSizeChangeViewState : MviViewState {
    val position: StateFlow<PerpsOpenPosition?>
    val mark: StateFlow<BigDecimal?>
    val priceDecimals: StateFlow<Int>
    val amount: StateFlow<String>
    val sizeInToken: StateFlow<Boolean>
    val sizePreview: StateFlow<PerpsSizePreview?>
    val autoCloseEstimate: StateFlow<PerpsAutoCloseEstimate?>
    val restingAutoClose: StateFlow<PerpsAutoCloseDraft?>
    val autoClose: StateFlow<PerpsAutoCloseDraft?>
    val autoCloseEdit: StateFlow<PerpsAutoCloseEdit>
    val autoCloseEditable: StateFlow<Boolean>
    val availableFormat: StateFlow<String>
    val refusal: StateFlow<Int?>
    val issue: StateFlow<PerpsSizeChangeIssue?>
    val canReview: StateFlow<Boolean>
}

sealed interface PerpsSizeChangeAction : MviAction {
    data class SetAmount(val value: String) : PerpsSizeChangeAction
    data object ToggleSizeMode : PerpsSizeChangeAction
    data object Max : PerpsSizeChangeAction
    data class SetAutoClose(val draft: PerpsAutoCloseDraft?) : PerpsSizeChangeAction
    data object Retry : PerpsSizeChangeAction
    data class ShowRefusal(@StringRes val text: Int) : PerpsSizeChangeAction
}

data class PerpsSizePreview(val current: String, val next: String?)

sealed interface PerpsSizeChangeIssue {
    data object Loading : PerpsSizeChangeIssue
    data class Failed(val error: PerpsError) : PerpsSizeChangeIssue
    data object NoPosition : PerpsSizeChangeIssue
    data object TradingDisabled : PerpsSizeChangeIssue
    data class ExceedsMargin(val marginFormat: String) : PerpsSizeChangeIssue
    data class AboveMaximum(val maxMarginFormat: String) : PerpsSizeChangeIssue
    data class BelowMinimum(val minMargin: BigDecimal) : PerpsSizeChangeIssue
    data object OnlyFullClose : PerpsSizeChangeIssue
    data object InsufficientBalance : PerpsSizeChangeIssue
}

class PerpsSizeChangeFeature(
    marketIndex: Int,
    symbol: String,
    val side: PerpsOrderSide,
    val direction: PerpsSizeDirection,
    repository: PerpsRepository,
    adapter: PerpsPlannerAdapter,
    livePrices: PerpsLivePrices,
    unifiedAccountRepository: UnifiedAccountRepository,
    settingsRepository: SettingsRepository,
) : PerpsPositionFormFeature<PerpsSizeChangeViewState, PerpsSizeChangeAction>(
    marketIndex = marketIndex,
    symbol = symbol,
    repository = repository,
    adapter = adapter,
    livePrices = livePrices,
    unifiedAccountRepository = unifiedAccountRepository,
    settingsRepository = settingsRepository,
    isRetry = { it is PerpsSizeChangeAction.Retry },
    isEnabled = { flags ->
        if (direction == PerpsSizeDirection.ADD) {
            flags.openEnabled
        } else {
            flags.closeEnabled
        }
    },
),
    PerpsSizeChangeViewState {

    override val availableFormat = combine(availableBalance, position) { balance, position ->
        if (direction == PerpsSizeDirection.ADD) {
            balance
        } else {
            position?.margin
        }
    }
        .toAvailableFormat()

    private data class AmountInput(val text: String, val inToken: Boolean)

    private data class SizeAmounts(
        val delta: BigDecimal,
        val available: BigDecimal?,
        val bounds: PerpsPositionBounds?,
    )

    private sealed interface AmountCmd {
        data class Set(val value: String) : AmountCmd
        data object Toggle : AmountCmd
        data class Max(val amount: BigDecimal?) : AmountCmd
    }

    private val sizeDecimals = rules
        .map { it?.sizeDecimals }
        .cacheState(initialValue = null)

    private val amountInput = merge(
        actions
            .on<PerpsSizeChangeAction.SetAmount>()
            .map { AmountCmd.Set(it.value) },
        actions
            .on<PerpsSizeChangeAction.ToggleSizeMode>()
            .map { AmountCmd.Toggle },
        actions
            .on<PerpsSizeChangeAction.Max>()
            .withLatestFrom(availableBalance) { _, balance -> balance }
            .withLatestFrom(bounds) { balance, bounds ->
                AmountCmd.Max(bounds?.maxAddMargin ?: balance)
            },
    )
        .runningFold(AmountInput(text = "", inToken = false), ::reduceAmount)
        .cacheState(initialValue = AmountInput(text = "", inToken = false))

    override val amount = amountInput
        .map { it.text }
        .cacheState(initialValue = "")

    override val sizeInToken = amountInput
        .map { it.inToken }
        .cacheState(initialValue = false)

    private val marginDelta = amount
        .map { it.toBigDecimalOrNull() ?: BigDecimal.ZERO }
        .cacheState(initialValue = BigDecimal.ZERO)

    private val unmaskedSizePreview = combine(
        position,
        marginDelta,
        sizeInToken,
        mark,
        sizeDecimals,
        transform = { position, delta, inToken, mark, decimals ->
            preview(position, delta, inToken, mark, decimals)
        }
    )
        .cacheState(initialValue = null)

    override val sizePreview = combine(unmaskedSizePreview, settingsRepository.hiddenBalancesFlow) { preview, _ ->
        if (settingsRepository.hiddenBalances) {
            preview?.copy(current = HIDDEN_BALANCE, next = preview.next?.let { HIDDEN_BALANCE })
        } else {
            preview
        }
    }
        .cacheState(initialValue = null)

    override val autoCloseEstimate = combine(
        position,
        marginDelta,
        mark,
        sizeDecimals,
        transform = ::estimate,
    )
        .cacheState(initialValue = null)

    private val autoCloseRequest = actions
        .on<PerpsSizeChangeAction.SetAutoClose>()
        .withLatestFrom(detail) { action, detail ->
            PerpsAutoCloseEdit.Set(action.draft.withRestingLegs(direction, detail?.legsDraft())) as PerpsAutoCloseEdit
        }
        .cacheState(initialValue = PerpsAutoCloseEdit.Keep)

    override val autoCloseEdit = combine(
        autoCloseRequest,
        autoCloseEstimate,
        position,
        mark,
        priceDecimals,
        transform = ::repricedEdit,
    )
        .cacheState(initialValue = PerpsAutoCloseEdit.Keep)

    override val restingAutoClose = detail
        .map { it?.legsDraft() }
        .cacheState(initialValue = null)

    override val autoClose = combine(
        autoCloseEdit,
        detail,
        transform = { edit, detail ->
            when (edit) {
                PerpsAutoCloseEdit.Keep -> detail?.legsDraft()
                is PerpsAutoCloseEdit.Set -> edit.target
            }
        }
    )
        .cacheState(initialValue = null)

    override val autoCloseEditable = combine(
        flags,
        detail,
        transform = { flags, detail -> flags.autoCloseEnabled && detail?.autoCloseKnown == true }
    )
        .cacheState(initialValue = false)

    private val amounts = combine(
        marginDelta,
        availableBalance,
        bounds,
        transform = { delta, available, bounds ->
            SizeAmounts(delta = delta, available = available, bounds = bounds)
        }
    )

    override val issue = combine(
        gate,
        amounts,
        settingsRepository.hiddenBalancesFlow,
        transform = { gate, amounts, _ -> issue(gate, amounts) }
    )
        .cacheState(initialValue = PerpsSizeChangeIssue.Loading)

    override val refusal = merge(
        actions
            .on<PerpsSizeChangeAction.ShowRefusal>()
            .map { it.text },
        actions
            .filter { it !is PerpsSizeChangeAction.ShowRefusal }
            .map { null },
    )
        .cacheState(initialValue = null)

    override val canReview = combine(
        issue,
        marginDelta,
        transform = { issue, delta -> issue == null && delta.signum() > 0 }
    )
        .cacheState(initialValue = false)

    private fun issue(gate: PerpsPositionFormGate, amounts: SizeAmounts): PerpsSizeChangeIssue? {
        val margin = gate.detail?.position?.margin
        val bounds = amounts.bounds
        val delta = amounts.delta

        return when {
            !gate.isMultichain -> PerpsSizeChangeIssue.TradingDisabled
            gate.error != null -> PerpsSizeChangeIssue.Failed(gate.error)
            gate.loading -> PerpsSizeChangeIssue.Loading
            gate.detail == null -> PerpsSizeChangeIssue.NoPosition
            !gate.enabled -> PerpsSizeChangeIssue.TradingDisabled
            direction == PerpsSizeDirection.REDUCE && bounds != null && bounds.noPartialReduce(margin) ->
                PerpsSizeChangeIssue.OnlyFullClose

            delta.signum() <= 0 -> null
            direction == PerpsSizeDirection.ADD -> {
                val available = amounts.available ?: bounds?.maxAddMargin ?: BigDecimal.ZERO
                val ceiling = bounds?.maxAddMargin
                when {
                    delta > available -> PerpsSizeChangeIssue.InsufficientBalance
                    ceiling != null && delta > ceiling -> PerpsSizeChangeIssue.AboveMaximum(maskedUsd(ceiling))
                    else -> null
                }
            }

            margin != null && delta >= margin -> exceedsMargin(margin)
            bounds == null -> null
            bounds.minReduceMargin != null && delta < bounds.minReduceMargin ->
                PerpsSizeChangeIssue.BelowMinimum(bounds.minReduceMargin)

            bounds.maxReduceMargin != null && delta > bounds.maxReduceMargin ->
                exceedsMargin(bounds.maxReduceMargin)

            else -> null
        }
    }

    private fun exceedsMargin(margin: BigDecimal): PerpsSizeChangeIssue {
        return PerpsSizeChangeIssue.ExceedsMargin(maskedUsd(margin))
    }

    private fun maskedUsd(amount: BigDecimal): String {
        return maskIf(settingsRepository.hiddenBalances) { formatUsdAmount(amount) }
    }

    private fun preview(
        position: PerpsOpenPosition?,
        delta: BigDecimal,
        inToken: Boolean,
        mark: BigDecimal?,
        decimals: Int?,
    ): PerpsSizePreview? {
        position ?: return null
        val current = if (inToken) {
            position.size
        } else {
            position.positionValue
        } ?: return null
        val currentText = if (inToken) {
            formatTokenAmount(current, symbol, decimals)
        } else {
            formatUsdAmount(current)
        }
        val change = if (delta.signum() > 0) {
            change(position, current, delta, inToken, mark, decimals)
        } else {
            null
        } ?: return PerpsSizePreview(current = currentText, next = null)
        val next = applyChange(current, change)

        return PerpsSizePreview(
            current = currentText,
            next = if (inToken) {
                formatTokenAmount(next, symbol, decimals)
            } else {
                formatUsdAmount(next)
            },
        )
    }

    private fun repricedEdit(
        edit: PerpsAutoCloseEdit,
        estimate: PerpsAutoCloseEstimate?,
        position: PerpsOpenPosition?,
        mark: BigDecimal?,
        priceDecimals: Int,
    ): PerpsAutoCloseEdit {
        val target = (edit as? PerpsAutoCloseEdit.Set)?.target ?: return edit
        val entryPrice = estimate?.referencePrice ?: position?.avgEntryPrice ?: mark ?: return edit
        val leverage = position?.autoCloseLeverage() ?: BigDecimal.ONE

        return PerpsAutoCloseEdit.Set(target.repriced(entryPrice, leverage, priceDecimals, side))
    }

    private fun estimate(
        position: PerpsOpenPosition?,
        delta: BigDecimal,
        mark: BigDecimal?,
        decimals: Int?,
    ): PerpsAutoCloseEstimate? {
        val current = position?.size ?: return null
        val entry = position.avgEntryPrice ?: mark
        val change = if (delta.signum() > 0) {
            change(position, current, delta, inToken = true, mark = mark, decimals = decimals)
        } else {
            null
        }
        if (change == null) {
            return autoCloseEstimate(current, entry)
        }
        val next = applyChange(current, change)
        if (direction == PerpsSizeDirection.REDUCE || entry == null || mark == null || next.signum() <= 0) {
            return autoCloseEstimate(next, entry)
        }
        val blendedEntry = current.multiply(entry)
            .add(change.multiply(mark))
            .divide(next, maxOf(mark.scale(), entry.scale()), RoundingMode.HALF_UP)

        return autoCloseEstimate(next, blendedEntry)
    }

    private fun change(
        position: PerpsOpenPosition,
        current: BigDecimal,
        delta: BigDecimal,
        inToken: Boolean,
        mark: BigDecimal?,
        decimals: Int?,
    ): BigDecimal? {
        val scale = decimals ?: DEFAULT_SIZE_DECIMALS
        if (direction == PerpsSizeDirection.REDUCE) {
            val margin = position.margin?.takeIf { it.signum() > 0 } ?: return null
            return current.multiply(delta).divide(margin, scale, RoundingMode.DOWN)
        }
        val notional = delta.multiply(BigDecimal(position.leverage ?: return null))
        if (!inToken) {
            return notional
        }
        if (mark == null || mark.signum() <= 0) {
            return null
        }

        return notional.divide(mark, scale, RoundingMode.DOWN)
    }

    private fun applyChange(current: BigDecimal, change: BigDecimal): BigDecimal {
        return if (direction == PerpsSizeDirection.ADD) {
            current.add(change)
        } else {
            current.subtract(change).max(BigDecimal.ZERO)
        }
    }

    private fun reduceAmount(input: AmountInput, cmd: AmountCmd): AmountInput = when (cmd) {
        is AmountCmd.Set -> input.copy(
            text = limitFractionDigits(sanitizeAmount(cmd.value), PERPS_QUOTE_INPUT_SCALE),
        )

        AmountCmd.Toggle -> input.copy(inToken = !input.inToken)

        is AmountCmd.Max -> {
            val amount = cmd.amount
            if (amount != null && direction == PerpsSizeDirection.ADD) {
                input.copy(text = limitFractionDigits(formatPlainDecimal(amount), PERPS_QUOTE_INPUT_SCALE))
            } else {
                input
            }
        }
    }

    private companion object {
        const val DEFAULT_SIZE_DECIMALS = 6
    }
}

internal fun PerpsAutoCloseDraft?.withRestingLegs(
    direction: PerpsSizeDirection,
    resting: PerpsAutoCloseDraft?,
): PerpsAutoCloseDraft? {
    if (direction != PerpsSizeDirection.ADD || resting == null) {
        return this
    }

    return PerpsAutoCloseDraft(
        takeProfit = this?.takeProfit ?: resting.takeProfit,
        stopLoss = this?.stopLoss ?: resting.stopLoss,
        takeProfitRoi = this?.takeProfitRoi,
        stopLossRoi = this?.stopLossRoi,
    )
}

private fun PerpsPositionBounds.noPartialReduce(margin: BigDecimal?): Boolean {
    val min = minReduceMargin ?: return true
    return (maxReduceMargin != null && min > maxReduceMargin) || (margin != null && min >= margin)
}

/*
 *   PerpsPositionFormFeature ──► availableBalance? / rules? / detail? / position? / mark? / bounds? / gate
 *   availableBalance? / position? ──► availableFormat
 *
 *   rules? ──► sizeDecimals?
 *
 *                                SetAmount ──┐
 *                             ToggleSizeMode ┼──► [fold] amountInput ──┬──► amount ──► marginDelta
 *   Max, availableBalance? ⋯, bounds? ⋯──────┘                          └──► sizeInToken
 *
 *   position? / marginDelta / sizeInToken / mark? / sizeDecimals? ──► unmaskedSizePreview? ──┐
 *                                                   hiddenBalancesFlow ──┴──► sizePreview?
 *   position? / marginDelta / mark? / sizeDecimals? ──► autoCloseEstimate?
 *
 *   SetAutoClose, detail? ⋯──► autoCloseRequest ──┐
 *   autoCloseEstimate? / position? / mark? / priceDecimals ──┴──► autoCloseEdit ──┐
 *                                                                     detail? ──┴──► autoClose?
 *
 *   detail? ──► restingAutoClose?
 *
 *                       flags ──┐
 *                     detail? ──┴──► autoCloseEditable
 *
 *                                               gate ──┐
 *                                                      ├──► issue?
 *   marginDelta / availableBalance? / bounds? ──► amounts ──┤
 *                                    hiddenBalancesFlow ──┘
 *
 *                       issue? ──┐
 *                  marginDelta ──┴──► canReview
 */
