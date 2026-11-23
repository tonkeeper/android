package com.tonapps.perps

import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.core.balanceBucketOf
import com.tonapps.bus.generated.Events
import com.tonapps.bus.generated.Events.Perps.PerpsAmountBucket
import com.tonapps.bus.generated.Events.Perps.PerpsEntryPoint
import com.tonapps.bus.generated.Events.Perps.PerpsErrorType
import com.tonapps.bus.generated.Events.Perps.PerpsOrderType
import com.tonapps.bus.generated.Events.Perps.PerpsScreen
import com.tonapps.bus.generated.Events.Perps.PerpsSide
import com.tonapps.bus.generated.Events.Perps.PerpsWalletMode
import com.tonapps.deposit.multicoin.screens.confirm.base.ConfirmationError
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.USD_CURRENCY
import com.tonapps.perps.funding.PerpsDepositQuoteError
import com.tonapps.perps.funding.PerpsDepositSource
import com.tonapps.perps.funding.PerpsDepositSources
import com.tonapps.perps.funding.PerpsFundingAmount
import com.tonapps.perps.trade.PerpsTradeRequest
import com.tonapps.wallet.data.multichain.asset.perpsAssetId
import java.math.BigDecimal
import java.math.MathContext

object PerpsAnalytics {

    private val events: Events.Perps
        get() = AnalyticsHelper.Default.events.perps

    private val minimumDepositUsd: BigDecimal =
        PerpsFundingAmount.toDecimal(PerpsFundingAmount.GATEWAY_MINIMUM_USDC_UNITS, PerpsFundingAmount.USDC_DECIMALS)

    fun sectionOpened(entryPoint: PerpsEntryPoint) {
        events.perpsSectionOpened(entryPoint)
    }

    fun depositStarted(listing: PerpsDepositSources?) {
        val sources = listing?.takeIf { it.fresh }?.items
        val eligible = sources?.count { source ->
            sources.usdValue(source, source.balance)?.let { it >= minimumDepositUsd } == true
        }
        events.perpsDepositStarted(PerpsWalletMode.Multi, eligible)
    }

    fun depositContinueClicked(
        source: PerpsDepositSource,
        amount: BigDecimal,
        typedFiat: BigDecimal?,
        sources: List<PerpsDepositSource>,
    ) {
        val usd = typedFiat?.let { sources.usdOfFiat(source, it) } ?: sources.usdValue(source, amount)
        val bucket = usd?.let { PerpsAmountBucket.valueOf(balanceBucketOf(it.toDouble()).name) }
        events.perpsDepositContinueClicked(source.assetId, bucket)
    }

    fun depositAmountFailed(error: PerpsDepositQuoteError) {
        val errorType = when (error) {
            is PerpsDepositQuoteError.BelowMinimum -> PerpsErrorType.BelowMinimum
            PerpsDepositQuoteError.InsufficientBalance -> PerpsErrorType.InsufficientBalance
            is PerpsDepositQuoteError.InsufficientNetworkFee -> PerpsErrorType.InsufficientNetworkFee
            PerpsDepositQuoteError.ServiceUnavailable -> PerpsErrorType.ServiceUnavailable
            PerpsDepositQuoteError.QuoteFailed -> PerpsErrorType.NoQuote
            PerpsDepositQuoteError.AddressBlocked -> PerpsErrorType.AddressBlocked
            PerpsDepositQuoteError.Failed -> PerpsErrorType.Failed
        }
        events.perpsDepositFailed(PerpsScreen.Amount, errorType)
    }

    fun depositAddressBlockedInBackground() {
        events.perpsDepositFailed(PerpsScreen.Background, PerpsErrorType.AddressBlocked)
    }

    fun depositAmountInFlight() {
        events.perpsDepositFailed(PerpsScreen.Amount, PerpsErrorType.DepositInProgress)
    }

    fun depositConfirmFailed(error: ConfirmationError) {
        val errorType = when (error) {
            ConfirmationError.InsufficientBalance -> PerpsErrorType.InsufficientBalance
            is ConfirmationError.InsufficientNetworkFee -> PerpsErrorType.InsufficientNetworkFee
            ConfirmationError.Expired -> PerpsErrorType.QuoteExpired
            ConfirmationError.DepositInFlight -> PerpsErrorType.DepositInProgress
            is ConfirmationError.DepositBelowMinimum -> PerpsErrorType.BelowMinimum
            else -> PerpsErrorType.Failed
        }
        events.perpsDepositFailed(PerpsScreen.Confirm, errorType)
    }

    fun depositQuoteChanged() {
        events.perpsDepositFailed(PerpsScreen.Confirm, PerpsErrorType.QuoteChanged)
    }

    fun depositConfirmed(deposit: ConfirmType.PerpetualDeposit, assetId: String, operationId: String) {
        val sourceScreen = deposit.sourceScreen ?: return
        events.perpsDepositConfirmed(
            sourceScreen = sourceScreen,
            asset = assetId,
            operationId = operationId,
        )
    }

    fun orderSubmitted(request: PerpsTradeRequest.Open, clientOrderIndex: Long) {
        events.perpsOrderSubmitted(
            side = when (request.side) {
                PerpsOrderSide.LONG -> PerpsSide.Long
                PerpsOrderSide.SHORT -> PerpsSide.Short
            },
            asset = perpsAssetId(request.marketIndex),
            leverage = request.leverage.toDouble(),
            orderType = if (request.limitPrice == null) {
                PerpsOrderType.Market
            } else {
                PerpsOrderType.Limit
            },
            clientOrderIndex = clientOrderIndex,
        )
    }

    fun tradingDataError(marketIndex: Int) {
        events.perpsTradingDataError(perpsAssetId(marketIndex))
    }

    private fun List<PerpsDepositSource>.usdValue(source: PerpsDepositSource, amount: BigDecimal): BigDecimal? {
        val price = source.price?.takeIf { it.signum() > 0 } ?: return null
        return usdOfFiat(source, amount.multiply(price))
    }

    private fun List<PerpsDepositSource>.usdOfFiat(source: PerpsDepositSource, fiat: BigDecimal): BigDecimal? {
        val dollar = if (source.account.rate?.currencyCode.equals(USD_CURRENCY, ignoreCase = true)) {
            BigDecimal.ONE
        } else {
            firstOrNull { it.isBestValue }?.price?.takeIf { it.signum() > 0 } ?: return null
        }
        return fiat.divide(dollar, MathContext.DECIMAL64)
    }
}
