package com.tonapps.perps.data

import java.math.BigDecimal
import java.math.RoundingMode

data class PerpsTradingFlags(
    val openEnabled: Boolean,
    val closeEnabled: Boolean,
    val cancelEnabled: Boolean,
    val addMarginEnabled: Boolean,
    val removeMarginEnabled: Boolean,
    val autoCloseEnabled: Boolean,
) {
    companion object {
        val ALL_ENABLED = PerpsTradingFlags(
            openEnabled = true,
            closeEnabled = true,
            cancelEnabled = true,
            addMarginEnabled = true,
            removeMarginEnabled = true,
            autoCloseEnabled = true,
        )

        val NONE = PerpsTradingFlags(
            openEnabled = false,
            closeEnabled = false,
            cancelEnabled = false,
            addMarginEnabled = false,
            removeMarginEnabled = false,
            autoCloseEnabled = false,
        )
    }
}

data class PerpsLimitOrder(
    val orderId: String,
    val side: PerpsPositionSide,
    val remainingBase: BigDecimal?,
    val limitPrice: BigDecimal?,
)

enum class PerpsAutoCloseLegKind {
    TAKE_PROFIT,
    STOP_LOSS,
}

data class PerpsAutoCloseLeg(
    val kind: PerpsAutoCloseLegKind,
    val orderIndex: Long?,
    val triggerPrice: BigDecimal?,
    val sharePct: BigDecimal?,
    val projectedEquity: BigDecimal?,
    val projectedRoiPct: BigDecimal?,
)

data class PerpsOpenPosition(
    val id: String,
    val marketIndex: Int,
    val symbol: String,
    val side: PerpsPositionSide,
    val size: BigDecimal?,
    val positionValue: BigDecimal?,
    val avgEntryPrice: BigDecimal?,
    val markPrice: BigDecimal?,
    val liquidationPrice: BigDecimal?,
    val liquidationDistancePct: BigDecimal?,
    val margin: BigDecimal?,
    val unrealizedPnl: BigDecimal?,
    val roiPct: BigDecimal?,
    val leverage: Int?,
    val fundingPaid: BigDecimal?,
)

data class PerpsPositionDetail(
    val position: PerpsOpenPosition,
    val autoCloseKnown: Boolean,
    val takeProfit: PerpsAutoCloseLeg?,
    val stopLoss: PerpsAutoCloseLeg?,
)

enum class PerpsAutoCloseAffordance {
    SET_AUTO_CLOSE,
    SET_TAKE_PROFIT,
    SET_STOP_LOSS,
    NONE,
}

sealed interface PerpsPositionSection {
    data object Hidden : PerpsPositionSection
    data object Loading : PerpsPositionSection
    data class Failed(val error: PerpsError) : PerpsPositionSection
    data class Open(val detail: PerpsPositionDetail) : PerpsPositionSection
}

fun PerpsOpenPosition.withLiveMark(live: BigDecimal?): PerpsOpenPosition {
    if (live == null || live.signum() <= 0) {
        return this
    }
    val liveValue = size?.multiply(live) ?: positionValue
    val livePnl = if (avgEntryPrice != null && size != null) {
        val direction = if (side == PerpsPositionSide.LONG) {
            BigDecimal.ONE
        } else {
            MINUS_ONE
        }
        live.subtract(avgEntryPrice).multiply(size).multiply(direction)
    } else {
        unrealizedPnl
    }
    val liveRoi = if (livePnl != null && margin != null && margin.signum() > 0) {
        livePnl.multiply(HUNDRED).divide(margin, PERCENT_SCALE, RoundingMode.HALF_UP)
    } else {
        roiPct
    }
    val liveDistance = if (liquidationPrice != null && liquidationPrice.signum() > 0) {
        liquidationPrice.subtract(live)
            .multiply(HUNDRED)
            .divide(live, PERCENT_SCALE, RoundingMode.HALF_UP)
    } else {
        liquidationDistancePct
    }
    return copy(
        positionValue = liveValue,
        markPrice = live,
        liquidationDistancePct = liveDistance,
        unrealizedPnl = livePnl,
        roiPct = liveRoi,
    )
}

fun PerpsPositionDetail.toPositionLevels(): PerpsPositionLevels {
    return PerpsPositionLevels(
        entry = position.avgEntryPrice.toPositivePriceOrNull(),
        liquidation = position.liquidationPrice.toPositivePriceOrNull(),
        takeProfit = takeProfit?.triggerPrice.toPositivePriceOrNull(),
        stopLoss = stopLoss?.triggerPrice.toPositivePriceOrNull(),
    )
}

fun PerpsPositionDetail.autoCloseAffordance(flags: PerpsTradingFlags): PerpsAutoCloseAffordance {
    if (autoCloseKnown && flags.autoCloseEnabled) {
        return when {
            takeProfit == null && stopLoss == null -> PerpsAutoCloseAffordance.SET_AUTO_CLOSE
            takeProfit == null -> PerpsAutoCloseAffordance.SET_TAKE_PROFIT
            stopLoss == null -> PerpsAutoCloseAffordance.SET_STOP_LOSS
            else -> PerpsAutoCloseAffordance.NONE
        }
    }
    return PerpsAutoCloseAffordance.NONE
}

private fun BigDecimal?.toPositivePriceOrNull(): Double? {
    val value = this?.toDouble() ?: return null
    if (value.isFinite() && value > 0.0) {
        return value
    }
    return null
}

private const val PERCENT_SCALE = 10
private val HUNDRED = BigDecimal(100)
private val MINUS_ONE = BigDecimal(-1)
