package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpetualReview
import com.tonapps.chainkit.perpetual.PerpsOrderKind
import java.math.BigDecimal

internal fun PerpetualReview.Open.allocatedMargin(): BigDecimal {
    return if (kind == PerpsOrderKind.Limit) {
        BigDecimal.valueOf(notionalQuote, scale.quoteDecimals)
            .multiply(BigDecimal(initialMarginBps))
            .divide(BigDecimal(PerpsProductRules.IMF_TICKS))
    } else {
        BigDecimal.valueOf(estimatedCollateralQuote, scale.quoteDecimals)
    }
}

internal fun PerpetualReview.Open.youPay(): BigDecimal {
    return allocatedMargin().add(BigDecimal.valueOf(estimatedFeeQuote, scale.quoteDecimals))
}

internal fun PerpetualReview.Add.youPay(allocatedBefore: Long): BigDecimal {
    return BigDecimal.valueOf(estimatedCollateralQuote - allocatedBefore + estimatedFeeQuote, scale.quoteDecimals)
}
