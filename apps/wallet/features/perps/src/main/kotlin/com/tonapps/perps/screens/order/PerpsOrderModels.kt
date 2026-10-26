package com.tonapps.perps.screens.order

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
