package com.tonapps.perps.trade

import com.tonapps.chainkit.core.common.getOrElse
import com.tonapps.chainkit.lighter.trading.PerpsTradeBounds
import com.tonapps.chainkit.perpetual.PerpetualReview
import com.tonapps.chainkit.perpetual.PerpsMarketRules
import com.tonapps.chainkit.perpetual.PerpsOrderKind
import com.tonapps.chainkit.perpetual.PerpsSide
import com.tonapps.chainkit.perpetual.PerpsTradeException
import com.tonapps.log.L
import java.math.BigDecimal
import java.math.RoundingMode

data class PerpsEntryAutoCloseLimits(
    val closestTakeProfit: BigDecimal?,
    val closestStopLoss: BigDecimal?,
)

internal fun PerpsPlannedTrade.entryAutoCloseLimits(): PerpsEntryAutoCloseLimits? {
    val review = plan.review as? PerpetualReview.Open ?: return null

    return entryAutoCloseLimits(review, rules, markPrice)
}

internal fun entryAutoCloseLimits(
    review: PerpetualReview.Open,
    rules: PerpsMarketRules,
    markPrice: BigDecimal,
): PerpsEntryAutoCloseLimits? {
    val decimals = review.scale.priceDecimals
    val isLong = review.side == PerpsSide.Long
    val mark = markPrice.toFixedPrice(decimals)
    val reference = when {
        review.kind != PerpsOrderKind.Limit -> mark
        isLong -> minOf(mark, review.priceBoundOrLimit)
        else -> maxOf(mark, review.priceBoundOrLimit)
    }

    return try {
        val bounds = PerpsTradeBounds()
            .plannedAutoCloseBounds(review.side, rules, reference, review.estimatedEntryPrice, review.liquidationPrice)
            .getOrElse { throw it }
        val (takeProfit, stopLoss) = if (isLong) {
            bounds.takeProfit?.minPrice to bounds.stopLoss?.maxPrice
        } else {
            bounds.takeProfit?.maxPrice to bounds.stopLoss?.minPrice
        }

        PerpsEntryAutoCloseLimits(
            closestTakeProfit = takeProfit?.let { BigDecimal.valueOf(it, decimals) },
            closestStopLoss = stopLoss?.let { BigDecimal.valueOf(it, decimals) },
        )
    } catch (e: PerpsTradeException) {
        L.e(e)
        null
    }
}

private fun BigDecimal.toFixedPrice(decimals: Int): Long {
    return movePointRight(decimals).setScale(0, RoundingMode.HALF_UP).longValueExact()
}
