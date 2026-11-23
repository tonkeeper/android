package com.tonapps.trading.screens.shelves

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.raffle.entities.RaffleEntity
import java.math.BigDecimal
import java.math.RoundingMode

private const val POINTS_SCALE = 2

@Immutable
data class ShelvesRaffleCellItem(
    val raffleId: String,
    val title: String,
    @DrawableRes val iconRes: Int,
    val points: Points?,
) {

    @Immutable
    data class Points(
        val formatted: String,
        val wholeCount: Int?,
    )
}

internal fun RaffleEntity.toShelvesRaffleCellItem(): ShelvesRaffleCellItem {
    val points = leaderboard?.points
        ?.setScale(POINTS_SCALE, RoundingMode.DOWN)
        ?.takeIf { it.signum() > 0 }
    return ShelvesRaffleCellItem(
        raffleId = id,
        title = if (points == null) {
            compactBanner.defaultTitle
        } else {
            compactBanner.activeTitle
        },
        iconRes = raffleCellIconRes(compactBanner.iconId),
        points = points?.let {
            ShelvesRaffleCellItem.Points(
                formatted = CurrencyFormatter.formatFull("", Coins.of(it, POINTS_SCALE), POINTS_SCALE).toString(),
                wholeCount = wholePointsCount(it),
            )
        },
    )
}

private fun wholePointsCount(points: BigDecimal): Int? =
    if (points.stripTrailingZeros().scale() <= 0) {
        points.toInt()
    } else {
        null
    }

@DrawableRes
private fun raffleCellIconRes(iconId: String?): Int = when (iconId) {
    "dinner" -> UIKitIcon.ic_dinner_28
    else -> UIKitIcon.ic_ticket_28
}
