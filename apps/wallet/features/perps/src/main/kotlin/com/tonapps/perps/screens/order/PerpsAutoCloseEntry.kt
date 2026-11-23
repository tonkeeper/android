package com.tonapps.perps.screens.order

import com.tonapps.perps.data.PerpsAutoCloseDraft
import com.tonapps.perps.data.PerpsMarketRules
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.perpsLiquidationPrice
import com.tonapps.perps.trade.PerpsEntryAutoCloseLimits
import com.tonapps.wallet.localization.Localization
import java.math.BigDecimal

internal data class PerpsAutoCloseTerms(
    val limitPrice: BigDecimal?,
    val leverage: Int,
)

internal data class PerpsDraftLiquidation(
    val terms: PerpsAutoCloseTerms,
    val price: BigDecimal?,
    val entryLimits: PerpsEntryAutoCloseLimits? = null,
)

internal data class PerpsAutoCloseEntry(
    val draft: PerpsAutoCloseDraft?,
    val warning: PerpsAutoCloseIssue?,
    val checkedTerms: PerpsAutoCloseTerms? = null,
)

internal sealed interface PerpsAutoCloseCmd {
    data class Set(val draft: PerpsAutoCloseDraft?) : PerpsAutoCloseCmd
    data class Check(
        val terms: PerpsAutoCloseTerms,
        val mark: BigDecimal?,
        val liquidation: PerpsDraftLiquidation?,
        val priceDecimals: Int?,
    ) : PerpsAutoCloseCmd
}

internal fun PerpsDraftLiquidation?.priceFor(terms: PerpsAutoCloseTerms): BigDecimal? {
    return this?.takeIf { it.terms == terms }?.price
}

internal fun PerpsDraftLiquidation?.entryLimitsFor(terms: PerpsAutoCloseTerms): PerpsEntryAutoCloseLimits? {
    return this?.takeIf { it.terms == terms }?.entryLimits
}

internal fun resolveAutoCloseLiquidation(
    terms: PerpsAutoCloseTerms,
    reviewed: PerpsDraftLiquidation?,
    estimate: BigDecimal?,
): PerpsDraftLiquidation {
    return PerpsDraftLiquidation(
        terms,
        price = reviewed.priceFor(terms) ?: estimate,
        entryLimits = reviewed.entryLimitsFor(terms),
    )
}

internal fun autoCloseLiquidationEstimate(
    side: PerpsOrderSide,
    terms: PerpsAutoCloseTerms,
    mark: BigDecimal?,
    rules: PerpsMarketRules?,
): BigDecimal? {
    if (rules == null || mark == null) {
        return null
    }
    return perpsLiquidationPrice(
        side = side,
        entryPrice = terms.limitPrice ?: mark,
        markPrice = mark,
        leverage = terms.leverage,
        maintenanceMarginBps = rules.maintenanceMarginBps,
        priceDecimals = rules.priceDecimals,
    )?.takeIf { it.signum() > 0 }
}

internal fun PerpsAutoCloseEntry.reduce(side: PerpsOrderSide, cmd: PerpsAutoCloseCmd): PerpsAutoCloseEntry {
    val checkCmd = when (cmd) {
        is PerpsAutoCloseCmd.Set -> return copy(draft = cmd.draft, warning = null)
        is PerpsAutoCloseCmd.Check -> cmd
    }
    val liquidationFirstWarning = warning?.takeIf {
        it.text == Localization.perps_auto_close_sl_liquidation_first_above ||
            it.text == Localization.perps_auto_close_sl_liquidation_first_below
    }
    val quotedLiquidation = liquidationFirstWarning?.price
    val stricterLiquidation = checkCmd.liquidation.priceFor(checkCmd.terms)?.takeIf { liquidation ->
        when {
            quotedLiquidation == null -> true
            side == PerpsOrderSide.LONG -> liquidation > quotedLiquidation
            else -> liquidation < quotedLiquidation
        }
    }
    val entryForTerms = when {
        checkCmd.terms != checkedTerms -> copy(warning = null, checkedTerms = checkCmd.terms)
        liquidationFirstWarning != null && stricterLiquidation != null -> {
            copy(warning = liquidationFirstWarning.copy(price = stricterLiquidation))
        }
        else -> this
    }
    val stored = entryForTerms.draft ?: return entryForTerms
    val entryPrice = checkCmd.terms.limitPrice ?: checkCmd.mark ?: return entryForTerms
    val entryLimits = checkCmd.liquidation.entryLimitsFor(checkCmd.terms)
    val bounds = PerpsAutoCloseBounds(
        entryPrice = entryPrice,
        leverage = BigDecimal(checkCmd.terms.leverage),
        currentPrice = entryPrice,
        liquidationPrice = checkCmd.liquidation.priceFor(checkCmd.terms),
        closestTakeProfit = entryLimits?.closestTakeProfit,
        closestStopLoss = entryLimits?.closestStopLoss,
    )
    val priceDecimals = checkCmd.priceDecimals
    val typedByPercent = stored.takeProfitRoi != null || stored.stopLossRoi != null
    if (priceDecimals == null && typedByPercent) {
        return entryForTerms
    }
    if (priceDecimals != null && stored.hasUnplaceableRoi(bounds.entryPrice, bounds.leverage, priceDecimals, side)) {
        return entryForTerms.copy(warning = null)
    }
    val draft = if (priceDecimals == null) {
        stored
    } else {
        stored.repriced(bounds.entryPrice, bounds.leverage, priceDecimals, side)
    }
    val check = checkAutoClose(side, draft.takeProfit, draft.stopLoss, bounds)
    if (check.isValid) {
        return entryForTerms.copy(draft = draft)
    }
    val takeProfitValid = check.takeProfit == null
    val stopLossValid = check.stopLoss == null
    val kept = PerpsAutoCloseDraft(
        takeProfit = draft.takeProfit.takeIf { takeProfitValid },
        stopLoss = draft.stopLoss.takeIf { stopLossValid },
        takeProfitRoi = draft.takeProfitRoi.takeIf { takeProfitValid },
        stopLossRoi = draft.stopLossRoi.takeIf { stopLossValid },
    )

    return entryForTerms.copy(
        draft = kept.takeIf { it.takeProfit != null || it.stopLoss != null },
        warning = check.takeProfit ?: check.stopLoss,
    )
}
