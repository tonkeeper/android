package com.tonapps.perps.screens.details

import androidx.compose.runtime.Immutable
import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.PerpsAutoCloseLeg
import com.tonapps.perps.data.PerpsAutoCloseLegKind
import com.tonapps.perps.data.PerpsOpenPosition
import com.tonapps.perps.data.PerpsPositionDetail
import com.tonapps.perps.data.PerpsPositionSection
import com.tonapps.perps.data.PerpsPositionSide
import com.tonapps.perps.data.autoCloseLeverage
import com.tonapps.perps.data.formatSignedPercent
import com.tonapps.perps.data.formatSignedUsdAmount
import com.tonapps.perps.data.formatTokenAmount
import com.tonapps.perps.data.formatUsd
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.data.maskIf
import com.tonapps.perps.data.toOrderSide
import com.tonapps.perps.screens.order.autoCloseEstimate
import com.tonapps.perps.screens.order.legPnl
import com.tonapps.perps.screens.order.roiFromPrice
import java.math.BigDecimal

@Immutable
data class PerpsPositionView(
    val section: PerpsPositionSection,
    val card: PerpsPositionCardUi?,
)

@Immutable
data class PerpsPositionCardUi(
    val equityFormat: String,
    val pnl: BigDecimal?,
    val pnlFormat: String,
    val roiFormat: String?,
    val sizeUsdFormat: String,
    val sizeTokenFormat: String,
    val liquidationPriceFormat: String,
    val liquidationDistanceFormat: String?,
    val markPriceFormat: String,
    val entryPriceFormat: String,
    val fundingFormat: String,
    val takeProfit: PerpsAutoCloseLegUi?,
    val stopLoss: PerpsAutoCloseLegUi?,
)

@Immutable
data class PerpsAutoCloseLegUi(
    val kind: PerpsAutoCloseLegKind,
    val triggerPriceFormat: String,
    val projectedPnlFormat: String,
    val projectedRoi: BigDecimal?,
    val projectedRoiFormat: String?,
)

internal fun PerpsPositionDetail.toCardUi(
    symbol: String,
    sizeDecimals: Int?,
    hiddenBalance: Boolean,
): PerpsPositionCardUi = PerpsPositionCardUi(
    equityFormat = maskIf(hiddenBalance) { position.equity?.let { formatUsdAmount(it) } ?: PERPS_EMPTY_VALUE },
    pnl = position.unrealizedPnl.takeUnless { hiddenBalance },
    pnlFormat = maskIf(hiddenBalance) {
        position.unrealizedPnl?.let { formatSignedUsdAmount(it) } ?: PERPS_EMPTY_VALUE
    },
    roiFormat = if (hiddenBalance) {
        null
    } else {
        position.roiPct?.let { formatSignedPercent(it) } ?: PERPS_EMPTY_VALUE
    },
    sizeUsdFormat = maskIf(hiddenBalance) { position.positionValue?.let { formatUsdAmount(it) } ?: PERPS_EMPTY_VALUE },
    sizeTokenFormat = maskIf(hiddenBalance) {
        position.size?.let { formatTokenAmount(it, symbol, sizeDecimals) } ?: PERPS_EMPTY_VALUE
    },
    liquidationPriceFormat = position.liquidationPrice?.let { formatUsd(it) } ?: PERPS_EMPTY_VALUE,
    liquidationDistanceFormat = position.liquidationDistancePct?.let { formatSignedPercent(it) },
    markPriceFormat = position.markPrice?.let { formatUsd(it) } ?: PERPS_EMPTY_VALUE,
    entryPriceFormat = position.avgEntryPrice?.let { formatUsd(it) } ?: PERPS_EMPTY_VALUE,
    fundingFormat = maskIf(hiddenBalance) {
        position.fundingPaid?.let { formatSignedUsdAmount(it) } ?: PERPS_EMPTY_VALUE
    },
    takeProfit = takeProfit?.toUi(position, hiddenBalance),
    stopLoss = stopLoss?.toUi(position, hiddenBalance),
)

private fun PerpsAutoCloseLeg.toUi(
    position: PerpsOpenPosition,
    hiddenBalance: Boolean,
): PerpsAutoCloseLegUi {
    val roi = projectedRoiPct ?: projectedRoi(position)
    return PerpsAutoCloseLegUi(
        kind = kind,
        triggerPriceFormat = triggerPrice?.let { formatUsd(it) } ?: PERPS_EMPTY_VALUE,
        projectedPnlFormat = maskIf(hiddenBalance) {
            projectedPnl(position)?.let { formatSignedUsdAmount(it) } ?: PERPS_EMPTY_VALUE
        },
        projectedRoi = roi.takeUnless { hiddenBalance },
        projectedRoiFormat = if (hiddenBalance) {
            null
        } else {
            roi?.let { formatSignedPercent(it) } ?: PERPS_EMPTY_VALUE
        },
    )
}

private fun PerpsAutoCloseLeg.projectedPnl(position: PerpsOpenPosition): BigDecimal? {
    val trigger = triggerPrice ?: return null
    val estimate = autoCloseEstimate(position.size, position.avgEntryPrice) ?: return null
    return legPnl(estimate, trigger, position.side == PerpsPositionSide.LONG)
}

private fun PerpsAutoCloseLeg.projectedRoi(position: PerpsOpenPosition): BigDecimal? {
    val trigger = triggerPrice ?: return null
    val entryPrice = position.avgEntryPrice?.takeIf { it.signum() > 0 } ?: return null
    return roiFromPrice(entryPrice, trigger, position.autoCloseLeverage(), position.side.toOrderSide())
}
