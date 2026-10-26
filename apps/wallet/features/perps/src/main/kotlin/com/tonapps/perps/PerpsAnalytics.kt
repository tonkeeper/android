package com.tonapps.perps

import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.Perps.PerpsEntryPoint
import com.tonapps.bus.generated.Events.Perps.PerpsOrderType
import com.tonapps.bus.generated.Events.Perps.PerpsReason
import com.tonapps.bus.generated.Events.Perps.PerpsSide
import com.tonapps.bus.generated.Events.Perps.PerpsWalletType
import com.tonapps.chainkit.perpetual.PerpsTradeError
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.PerpsSendReason
import com.tonapps.perps.screens.review.PerpsReviewArgs
import com.tonapps.perps.trade.PerpsSubmitStart
import com.tonapps.perps.trade.PerpsTradeFailure
import com.tonapps.wallet.data.multichain.asset.perpsAssetId
import java.math.BigDecimal

object PerpsAnalytics {

    private val events: Events.Perps
        get() = AnalyticsHelper.Default.events.perps

    fun sectionOpened(entryPoint: PerpsEntryPoint) {
        events.perpsSectionOpened(entryPoint)
    }

    fun depositStarted() {
        events.perpsDepositStarted(PerpsWalletType.Multichain)
    }

    fun orderSubmitted(args: PerpsReviewArgs.Open) {
        events.perpsOrderSubmitted(
            side = when (args.side) {
                PerpsOrderSide.LONG -> PerpsSide.Long
                PerpsOrderSide.SHORT -> PerpsSide.Short
            },
            asset = perpsAssetId(args.marketIndex),
            leverage = args.leverage.toDouble(),
            margin = BigDecimal(args.marginQuote).toDouble(),
            orderType = if (args.limitPrice == null) {
                PerpsOrderType.Market
            } else {
                PerpsOrderType.Limit
            },
        )
    }

    fun orderFailed(reason: PerpsReason) {
        events.perpsOrderFailed(reason)
    }

    fun tradingDataError(marketIndex: Int) {
        events.perpsTradingDataError(perpsAssetId(marketIndex))
    }
}

internal fun PerpsSubmitStart.failureReason(): PerpsReason? {
    return when (this) {
        is PerpsSubmitStart.Started -> null
        is PerpsSubmitStart.Requote -> PerpsReason.PriceMoved
        is PerpsSubmitStart.Blocked -> PerpsReason.Other
        is PerpsSubmitStart.Failed -> failure.failureReason()
    }
}

internal fun PerpsTradeFailure.failureReason(): PerpsReason {
    return when (this) {
        is PerpsTradeFailure.NonceUnavailable -> error.failureReason()
        is PerpsTradeFailure.SendFailed -> error.failureReason()
        is PerpsTradeFailure.SendRefused -> if (reason == PerpsSendReason.RESIGN_REQUIRED) {
            PerpsReason.Other
        } else {
            PerpsReason.VenueRejected
        }
        is PerpsTradeFailure.OrderRejected -> PerpsReason.VenueRejected
        is PerpsTradeFailure.PlannerRejected -> kind.failureReason()
        PerpsTradeFailure.OperationInProgress,
        PerpsTradeFailure.StaleInput,
        PerpsTradeFailure.Interrupted,
        PerpsTradeFailure.Unresolved -> PerpsReason.Other
    }
}

private fun PerpsError.failureReason(): PerpsReason {
    return when (this) {
        PerpsError.Network,
        PerpsError.ServiceUnavailable -> PerpsReason.NetworkError
        else -> PerpsReason.Other
    }
}

private fun PerpsTradeError.failureReason(): PerpsReason {
    return when (this) {
        PerpsTradeError.SlippageExceeded,
        PerpsTradeError.InsufficientLiquidity,
        PerpsTradeError.NeedsMoreDepth -> PerpsReason.PriceMoved
        PerpsTradeError.InsufficientBalance,
        PerpsTradeError.MarginBelowRequirement -> PerpsReason.InsufficientFunds
        PerpsTradeError.TradingDisabled,
        PerpsTradeError.UnsupportedMarket -> PerpsReason.VenueRejected
        else -> PerpsReason.Other
    }
}
