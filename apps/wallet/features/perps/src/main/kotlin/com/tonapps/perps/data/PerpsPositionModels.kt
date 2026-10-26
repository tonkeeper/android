package com.tonapps.perps.data

import androidx.annotation.StringRes
import com.tonapps.wallet.data.multichain.perps.PerpsOperationKind
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

        val VENUE_SILENT = PerpsTradingFlags(
            openEnabled = false,
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
    val orderIndex: Long,
    val clientOrderIndex: Long?,
    val side: PerpsPositionSide,
    val remainingBase: BigDecimal?,
    val limitPrice: BigDecimal?,
    val expiryUnixMs: Long? = null,
)

enum class PerpsAutoCloseLegKind {
    TAKE_PROFIT,
    STOP_LOSS,
}

data class PerpsAutoCloseLeg(
    val kind: PerpsAutoCloseLegKind,
    val orderIndex: Long?,
    val clientOrderIndex: Long?,
    val triggerPrice: BigDecimal?,
    val sharePct: BigDecimal?,
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
    val equity: BigDecimal?,
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
    data class Open(val detail: PerpsPositionDetail, val pnlPending: Boolean = false) : PerpsPositionSection
}

enum class PerpsOperationPillState {
    IN_PROGRESS,
    DONE,
}

data class PerpsOperationPill(
    val kind: PerpsOperationKind,
    val state: PerpsOperationPillState,
    val symbol: String,
    val side: PerpsOrderSide,
    val amount: String,
    val leverage: Int,
)

data class PerpsOperationFailure(
    val operationId: String,
    @StringRes val text: Int,
    val textArg: String? = null,
)
fun PerpsPositionSide.toOrderSide(): PerpsOrderSide {
    return when (this) {
        PerpsPositionSide.LONG -> PerpsOrderSide.LONG
        PerpsPositionSide.SHORT -> PerpsOrderSide.SHORT
    }
}

fun PerpsOpenPosition.withLiveMark(live: BigDecimal?): PerpsOpenPosition {
    if (live == null || live.signum() <= 0) {
        return this
    }
    return copy(markPrice = live)
}

fun PerpsOpenPosition.effectiveLeverage(): BigDecimal? {
    val leverage = leverage
    if (leverage != null) {
        return BigDecimal(leverage)
    }
    val value = positionValue
    val margin = margin
    if (value != null && margin != null && value.signum() > 0 && margin.signum() > 0) {
        return value.divide(margin, LEVERAGE_SCALE, RoundingMode.HALF_UP)
    }
    return null
}

fun PerpsOpenPosition.autoCloseLeverage(): BigDecimal {
    return effectiveLeverage() ?: BigDecimal.ONE
}

fun PerpsPositionDetail.toPositionLevels(): PerpsPositionLevels {
    return PerpsPositionLevels(
        entry = position.avgEntryPrice.toPositivePriceOrNull(),
        liquidation = position.liquidationPrice.toPositivePriceOrNull(),
        takeProfit = takeProfit?.triggerPrice.toPositivePriceOrNull(),
        stopLoss = stopLoss?.triggerPrice.toPositivePriceOrNull(),
    )
}

fun PerpsPositionDetail.withKnownLegs(previous: PerpsPositionDetail?): PerpsPositionDetail {
    if (autoCloseKnown || previous == null || previous.position.id != position.id) {
        return this
    }
    return copy(takeProfit = previous.takeProfit, stopLoss = previous.stopLoss)
}

fun PerpsPositionDetail.withLegs(draft: PerpsAutoCloseDraft?): PerpsPositionDetail {
    return copy(
        takeProfit = draft?.takeProfit?.let { autoCloseLeg(PerpsAutoCloseLegKind.TAKE_PROFIT, it) },
        stopLoss = draft?.stopLoss?.let { autoCloseLeg(PerpsAutoCloseLegKind.STOP_LOSS, it) },
    )
}

private fun autoCloseLeg(kind: PerpsAutoCloseLegKind, triggerPrice: BigDecimal): PerpsAutoCloseLeg {
    return PerpsAutoCloseLeg(
        kind = kind,
        orderIndex = null,
        clientOrderIndex = null,
        triggerPrice = triggerPrice,
        sharePct = null,
        projectedRoiPct = null,
    )
}

fun PerpsPositionDetail.legsDraft(): PerpsAutoCloseDraft? {
    val takeProfitPrice = takeProfit?.triggerPrice
    val stopLossPrice = stopLoss?.triggerPrice
    if (takeProfitPrice == null && stopLossPrice == null) {
        return null
    }

    return PerpsAutoCloseDraft(takeProfit = takeProfitPrice, stopLoss = stopLossPrice)
}

fun PerpsAutoCloseDraft?.matches(other: PerpsAutoCloseDraft?, priceDecimals: Int): Boolean {
    return this?.takeProfit.matchesPrice(other?.takeProfit, priceDecimals) &&
        this?.stopLoss.matchesPrice(other?.stopLoss, priceDecimals)
}

private fun BigDecimal?.matchesPrice(other: BigDecimal?, priceDecimals: Int): Boolean {
    if (this != null && other != null) {
        return setScale(priceDecimals, RoundingMode.HALF_UP)
            .compareTo(other.setScale(priceDecimals, RoundingMode.HALF_UP)) == 0
    }

    return this == null && other == null
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

private const val LEVERAGE_SCALE = 4
