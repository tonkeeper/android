package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpetualReview
import com.tonapps.chainkit.perpetual.PerpsFeeKind
import java.math.BigDecimal
import java.math.BigInteger

private class ReviewFields(
    val exact: List<Any?>,
    val within: List<Long?>,
    val withinUsd: List<Long>,
    val immediateRisk: Boolean = false,
)

internal fun PerpsPlannedTrade.keepsWithin(reviewed: PerpsPlannedTrade): Boolean {
    return plan.review.keepsWithin(reviewed.plan.review) &&
        listOf(PerpsFeeKind.Open, PerpsFeeKind.Close).all { kind ->
            displayedTakerFeePpm(kind) == reviewed.displayedTakerFeePpm(kind)
        }
}

internal fun PerpetualReview.keepsWithin(reviewed: PerpetualReview): Boolean {
    val actual = fields()
    val expected = reviewed.fields()
    val usdFloor = if (scale.quoteDecimals >= CENT_DECIMALS) {
        BigInteger.TEN.pow(scale.quoteDecimals - CENT_DECIMALS)
    } else {
        BigInteger.ZERO
    }

    return this::class == reviewed::class &&
        scale == reviewed.scale &&
        (!actual.immediateRisk || expected.immediateRisk) &&
        actual.exact == expected.exact &&
        actual.within.zip(expected.within).all { (value, reviewedValue) ->
            withinDrift(value, reviewedValue, BigInteger.ZERO)
        } &&
        actual.withinUsd.zip(expected.withinUsd).all { (value, reviewedValue) ->
            withinDrift(value, reviewedValue, usdFloor)
        }
}

private fun PerpetualReview.fields(): ReviewFields = when (this) {
    is PerpetualReview.Open -> ReviewFields(
        exact = listOf(side, kind, initialMarginBps, takeProfitTrigger, stopLossTrigger),
        within = listOf(baseAmount, priceBoundOrLimit, liquidationPrice),
        withinUsd = listOf(estimatedFeeQuote, estimatedCollateralQuote),
        immediateRisk = isImmediateRisk,
    )

    is PerpetualReview.Add -> ReviewFields(
        exact = listOf(side, kind, initialMarginBps, takeProfitTrigger, stopLossTrigger),
        within = listOf(baseAmount, priceBoundOrLimit, liquidationPrice),
        withinUsd = listOf(estimatedFeeQuote, estimatedCollateralQuote),
        immediateRisk = isImmediateRisk,
    )

    is PerpetualReview.Close -> ReviewFields(
        exact = listOf(side, fullExit),
        within = listOf(closingBaseAmount, priceBound, liquidationPrice),
        withinUsd = listOf(estimatedFeeQuote, estimatedReturnedMarginQuote),
        immediateRisk = isImmediateRisk,
    )

    is PerpetualReview.Margin -> ReviewFields(
        exact = listOf(addToPosition),
        within = listOf(liquidationPrice),
        withinUsd = listOf(amountQuote, allocatedMarginAfterQuote),
        immediateRisk = isImmediateRisk,
    )

    is PerpetualReview.AutoClose -> ReviewFields(
        exact = listOf(
            side,
            takeProfitTrigger,
            stopLossTrigger,
            takeProfitLimit,
            stopLossLimit,
            replacesTakeProfit,
            replacesStopLoss,
        ),
        within = listOf(positionBaseAmount, liquidationPrice),
        withinUsd = emptyList(),
    )

    is PerpetualReview.OrderCancel -> ReviewFields(
        exact = listOf(orderIndex),
        within = emptyList(),
        withinUsd = emptyList(),
    )

    is PerpetualReview.OrderModify -> ReviewFields(
        exact = listOf(orderIndex, positionTied, price, triggerPrice),
        within = listOf(baseAmount),
        withinUsd = emptyList(),
    )
}

private fun withinDrift(value: Long?, reviewed: Long?, floor: BigInteger): Boolean {
    if (value != null && reviewed != null) {
        val drift = BigInteger.valueOf(value).subtract(BigInteger.valueOf(reviewed)).abs()

        return drift <= floor || withinReviewDrift(BigDecimal.valueOf(value), BigDecimal.valueOf(reviewed))
    }

    return value == reviewed
}

internal fun withinReviewDrift(value: BigDecimal, reviewed: BigDecimal, leverage: BigDecimal = BigDecimal.ONE): Boolean {
    val allowed = reviewed.abs().multiply(BigDecimal.valueOf(PerpsProductRules.REVIEW_DRIFT_PERCENT))

    return value.subtract(reviewed).abs().multiply(leverage).multiply(PERCENT) <= allowed
}

private const val CENT_DECIMALS = 2

private val PERCENT = BigDecimal(100)
