package com.tonapps.perps.data

import io.perpsapi.models.Balance
import java.math.BigDecimal
import java.math.RoundingMode

data class PerpsBalance(
    val collateral: BigDecimal?,
    val availableBalance: BigDecimal?,
    val transferableBalance: BigDecimal?,
)

enum class PerpsPositionSide {
    LONG,
    SHORT,
}

data class PerpsPosition(
    val marketIndex: Int,
    val symbol: String,
    val iconUrl: String?,
    val side: PerpsPositionSide,
    val margin: BigDecimal?,
    val unrealizedPnl: BigDecimal?,
    val equity: BigDecimal?,
    val leverage: Int?,
)

fun List<PerpsPosition>.unrealizedPnlPercent(): BigDecimal? {
    if (isEmpty() || any { (it.margin?.signum() ?: 0) <= 0 }) {
        return null
    }
    val pnl = sumOf { it.unrealizedPnl ?: BigDecimal.ZERO }
    val margin = sumOf { it.margin ?: BigDecimal.ZERO }
    return pnl.multiply(BigDecimal(100)).divide(margin, 2, RoundingMode.HALF_UP)
}

data class PerpsPortfolioOrder(
    val marketIndex: Int,
    val orderIndex: Long,
    val clientOrderIndex: Long?,
    val symbol: String,
    val iconUrl: String?,
    val side: PerpsPositionSide,
    val remainingBase: BigDecimal?,
    val limitPrice: BigDecimal?,
    val leverage: Int?,
    val margin: BigDecimal?,
)

data class PerpsPortfolio(
    val balance: PerpsBalance?,
    val positions: List<PerpsPosition>,
    val openOrders: List<PerpsPortfolioOrder>?,
) {
    companion object {
        val EMPTY = PerpsPortfolio(balance = null, positions = emptyList(), openOrders = null)
    }
}

fun PerpsBalance.withdrawableBalance(): BigDecimal? = transferableBalance ?: availableBalance

fun PerpsBalance.spendableBalance(): BigDecimal? = availableBalance ?: collateral

internal fun Balance.spendableBalance(): String? = availableBalance ?: collateral
