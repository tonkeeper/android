package com.tonapps.perps.trade

import com.tonapps.chainkit.perpetual.PerpsTradeError
import com.tonapps.chainkit.perpetual.PerpsTradeException
import io.perpsapi.models.TradingScreen

internal const val MARKET_BINDING_FIELD = "market_binding"

internal object PerpsMarketBinding {

    fun requireRequestedMarket(marketId: Int, screen: TradingScreen) {
        screen.position?.let { position ->
            requireMarket(marketId, "the position", position.marketIndex)
        }
        screen.openOrders.orEmpty().forEach { order ->
            requireMarket(marketId, "order ${order.orderIndex}", order.marketIndex)
        }
    }

    private fun requireMarket(marketId: Int, subject: String, served: Int?) {
        if (served != marketId) {
            throw PerpsTradeException(
                PerpsTradeError.InvalidRules,
                "$subject is served for market $served, not the requested $marketId",
                MARKET_BINDING_FIELD,
            )
        }
    }
}
