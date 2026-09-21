package com.tonapps.perps.data

import java.math.BigDecimal
import java.math.RoundingMode

data class PerpsBalance(
    val availableBalance: BigDecimal?,
    val equity: BigDecimal?,
    val unrealizedPnlTotal: BigDecimal?,
)

/** Cost basis is `equity − unrealizedPnlTotal`. */
fun PerpsBalance.unrealizedPnlPercent(): BigDecimal? {
    val pnl = unrealizedPnlTotal ?: return null
    val costBasis = equity?.subtract(pnl) ?: return null
    if (costBasis.signum() == 0) {
        return null
    }
    return pnl.multiply(BigDecimal(100)).divide(costBasis, 2, RoundingMode.HALF_UP)
}

enum class PerpsPositionSide {
    LONG,
    SHORT,
}

data class PerpsPosition(
    val marketIndex: Int,
    val symbol: String,
    val iconUrl: String?,
    val side: PerpsPositionSide,
    val positionValue: BigDecimal?,
    val unrealizedPnl: BigDecimal?,
    val leverage: Int?,
)

data class PerpsPortfolio(
    val balance: PerpsBalance?,
    val positions: List<PerpsPosition>,
)
