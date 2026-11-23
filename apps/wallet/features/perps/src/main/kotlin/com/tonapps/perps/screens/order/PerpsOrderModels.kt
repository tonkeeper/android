package com.tonapps.perps.screens.order

import androidx.annotation.StringRes
import com.tonapps.perps.data.PerpsError
import java.math.BigDecimal

enum class PerpsOrderType {
    Market,
    Limit,
}

sealed interface PerpsOrderIssue {
    data object Loading : PerpsOrderIssue
    data class Failed(val error: PerpsError) : PerpsOrderIssue
    data object TradingDisabled : PerpsOrderIssue
    data class BelowMinMargin(val minMargin: BigDecimal, val canDeposit: Boolean) : PerpsOrderIssue
    data object InsufficientBalance : PerpsOrderIssue
    data object LimitPriceMissing : PerpsOrderIssue
}

data class PerpsAutoCloseEstimate(
    val sizeBase: BigDecimal,
    val referencePrice: BigDecimal,
)

data class PerpsLimitAutoClose(
    val takeProfit: BigDecimal?,
    val stopLoss: BigDecimal?,
    val removesPassedLeg: Boolean,
)

data class PerpsLimitPriceWarning(
    @StringRes val text: Int,
    val price: String? = null,
    @StringRes val removal: Int? = null,
) {
    val blocking: Boolean
        get() = removal == null
}
