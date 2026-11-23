package com.tonapps.perps.screens.details

import com.tonapps.chainkit.perpetual.PerpsSide
import com.tonapps.chainkit.perpetual.PerpsTradeIntent
import com.tonapps.perps.data.PerpsLimitOrder
import com.tonapps.perps.data.PerpsLimitOrderLeg
import com.tonapps.perps.data.PerpsPositionSide
import com.tonapps.perps.data.legsPassedBy
import com.tonapps.perps.trade.PerpsPlannedTrade
import com.tonapps.perps.trade.PerpsTradeRequest
import java.math.BigDecimal

data class PerpsLimitEditReview(
    val planned: PerpsPlannedTrade,
    val side: PerpsPositionSide,
    val base: BigDecimal,
    val baseDecimals: Int,
    val oldPrice: BigDecimal,
    val newPrice: BigDecimal,
    val priceDecimals: Int,
    val passedLegs: List<PerpsLimitOrderLeg>,
    val removesTakeProfit: Boolean,
    val removesStopLoss: Boolean,
)

internal fun limitEditReview(
    planned: PerpsPlannedTrade,
    order: PerpsLimitOrder,
): PerpsLimitEditReview? {
    val intent = planned.intent as? PerpsTradeIntent.OrderModify ?: return null
    val resting = planned.context.restingOrders.firstOrNull { it.orderIndex == intent.orderIndex } ?: return null
    val oldLimitPrice = resting.limitPrice ?: return null
    val scale = planned.rules.scale
    val signedPrice = BigDecimal.valueOf(intent.price, scale.priceDecimals)
    val passedLegs = order.legsPassedBy(signedPrice)

    return PerpsLimitEditReview(
        planned = planned,
        side = when (resting.side) {
            PerpsSide.Long -> PerpsPositionSide.LONG
            PerpsSide.Short -> PerpsPositionSide.SHORT
        },
        base = BigDecimal.valueOf(intent.baseAmount, scale.baseDecimals),
        baseDecimals = scale.baseDecimals,
        oldPrice = BigDecimal.valueOf(oldLimitPrice, scale.priceDecimals),
        newPrice = signedPrice,
        priceDecimals = scale.priceDecimals,
        passedLegs = passedLegs,
        removesTakeProfit = order.takeProfit?.let { it in passedLegs } == true,
        removesStopLoss = order.stopLoss?.let { it in passedLegs } == true,
    )
}

internal fun PerpsLimitEditReview.confirmedRequest(): PerpsTradeRequest.OrderModify {
    return (planned.request as PerpsTradeRequest.OrderModify).copy(
        maxBaseAmount = base,
        expectedLimitPrice = oldPrice,
    )
}
