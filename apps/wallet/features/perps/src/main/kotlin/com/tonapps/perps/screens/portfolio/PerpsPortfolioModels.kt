package com.tonapps.perps.screens.portfolio

import androidx.compose.runtime.Immutable
import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.PerpsPortfolioOrder
import com.tonapps.perps.data.PerpsPosition
import com.tonapps.perps.data.formatSignedUsdAmount
import com.tonapps.perps.data.formatUsdAmount
import com.tonapps.perps.data.maskIf
import java.math.BigDecimal

@Immutable
data class PerpsPortfolioUi(
    val balanceFormat: String,
    val funded: Boolean,
    val isEmpty: Boolean,
    val positions: List<PerpsPositionUi>,
    val positionsTotal: PerpsPositionsTotalUi,
    val orders: List<PerpsOrderUi>,
)

@Immutable
data class PerpsPositionUi(
    val position: PerpsPosition,
    val equityFormat: String,
    val pnl: BigDecimal?,
    val pnlFormat: String,
)

@Immutable
data class PerpsPositionsTotalUi(
    val amountFormat: String,
    val pnl: BigDecimal?,
    val pnlFormat: String,
    val pnlPercent: BigDecimal?,
    val pnlPercentFormat: String?,
)

@Immutable
data class PerpsOrderUi(
    val order: PerpsPortfolioOrder,
    val amountFormat: String,
    val limitPriceFormat: String,
)

fun PerpsPosition.toUi(hiddenBalance: Boolean): PerpsPositionUi = PerpsPositionUi(
    position = this,
    equityFormat = maskIf(hiddenBalance) { equity?.let { formatUsdAmount(it) } ?: PERPS_EMPTY_VALUE },
    pnl = unrealizedPnl.takeUnless { hiddenBalance },
    pnlFormat = maskIf(hiddenBalance) { unrealizedPnl?.let { formatSignedUsdAmount(it) } ?: PERPS_EMPTY_VALUE },
)
