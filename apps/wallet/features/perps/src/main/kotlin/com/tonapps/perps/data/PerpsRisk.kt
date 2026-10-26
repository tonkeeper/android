package com.tonapps.perps.data

import com.tonapps.chainkit.lighter.trading.LighterRisk
import com.tonapps.chainkit.lighter.trading.LighterTradeSide
import com.tonapps.perps.trade.PerpsProductRules
import java.math.BigDecimal

fun perpsLiquidationPrice(
    side: PerpsOrderSide,
    entryPrice: BigDecimal?,
    markPrice: BigDecimal?,
    leverage: Int,
    maintenanceMarginBps: Int?,
): BigDecimal? {
    val entry = entryPrice?.toDouble()?.takeIf { it > 0.0 } ?: return null
    val mark = markPrice?.toDouble()?.takeIf { it > 0.0 } ?: return null
    val maintenanceFraction = (maintenanceMarginBps ?: return null) / LighterRisk.MARGIN_FRACTION_DENOMINATOR
    val initialFraction = PerpsProductRules.initialMarginBps(leverage) / LighterRisk.MARGIN_FRACTION_DENOMINATOR
    val baseSize = PREVIEW_NOTIONAL / entry

    val estimate = LighterRisk.isolatedLiquidation(
        side = if (side == PerpsOrderSide.LONG) {
            LighterTradeSide.LONG
        } else {
            LighterTradeSide.SHORT
        },
        baseSize = baseSize,
        entryPrice = entry,
        markPrice = mark,
        maintenanceFraction = maintenanceFraction,
        collateralUsd = baseSize * mark * initialFraction,
    )
    return estimate.price?.toBigDecimal()
}

data class PerpsLiquidationPreview(
    val price: BigDecimal?,
    val isImmediateRisk: Boolean,
)

fun perpsPositionLiquidation(
    side: PerpsPositionSide,
    size: BigDecimal,
    entryPrice: BigDecimal,
    markPrice: BigDecimal,
    maintenanceMarginBps: Int,
    collateral: BigDecimal,
): PerpsLiquidationPreview {
    if (collateral.signum() <= 0) {
        return PerpsLiquidationPreview(price = null, isImmediateRisk = true)
    }
    val estimate = LighterRisk.isolatedLiquidation(
        side = if (side == PerpsPositionSide.LONG) {
            LighterTradeSide.LONG
        } else {
            LighterTradeSide.SHORT
        },
        baseSize = size.toDouble(),
        entryPrice = entryPrice.toDouble(),
        markPrice = markPrice.toDouble(),
        maintenanceFraction = maintenanceMarginBps / LighterRisk.MARGIN_FRACTION_DENOMINATOR,
        collateralUsd = collateral.toDouble(),
    )

    return PerpsLiquidationPreview(
        price = estimate.price?.toBigDecimal(),
        isImmediateRisk = estimate.isImmediateRisk,
    )
}

private const val PREVIEW_NOTIONAL = 1.0
