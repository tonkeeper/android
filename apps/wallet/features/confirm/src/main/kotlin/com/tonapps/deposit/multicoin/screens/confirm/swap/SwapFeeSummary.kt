package com.tonapps.deposit.multicoin.screens.confirm

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.chainkit.core.chain.model.transaction.Fee
import com.tonapps.deposit.multicoin.screens.confirm.base.PendingTransaction
import com.tonapps.deposit.multicoin.screens.confirm.base.Signing
import com.tonapps.deposit.multicoin.screens.confirm.fee.FeeAccount
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFee
import com.tonapps.deposit.multicoin.screens.confirm.fee.TxFeeLogic
import com.tonapps.wallet.data.multichain.exchange.SwapQuote

private const val ROUTE_EXPIRY_MARGIN_MS = 5_000L
private const val ROUTE_MIN_USABLE_MS = 5_000L
private const val ROUTE_MAX_USABLE_MS = 60_000L

sealed interface SwapFeeSummary {
    data class Charges(val count: Int) : SwapFeeSummary
    data class Amount(val text: String) : SwapFeeSummary
    data object BatteryEmpty : SwapFeeSummary
    data object LowBattery : SwapFeeSummary
    data class NoFunds(val symbol: String) : SwapFeeSummary
    data object Unknown : SwapFeeSummary

    val isUnpaid: Boolean
        get() = this is BatteryEmpty || this is LowBattery || this is NoFunds
}

sealed interface SwapFeePlan {
    data object Preparing : SwapFeePlan
    data class Ready(val summary: SwapFeeSummary, val quoteReceivedAt: Long?) : SwapFeePlan
    data object Failed : SwapFeePlan
}

data class SwapPriceChange(
    val side: Side,
    val previous: BigInteger,
    val current: BigInteger,
) {
    enum class Side { Receive, Send }
}

fun summarize(tx: PendingTransaction, pickedId: String?, amountBlocked: Boolean): SwapFeeSummary {
    if (tx.isFeeUnavailable) {
        return SwapFeeSummary.Unknown
    }
    val energy = tx.fee.energy ?: tx.account
    val soldIsEnergy = energy.asset.id == tx.account.asset.id
    val estimated = tx.fee.estimated
    val selected = tx.fee.resolve(pickedId)
    val selectedAccount = (selected?.account as? FeeAccount.Chain)?.value
    val signing = tx.signing as? Signing.Tx
    val sent = selected?.let { signing?.sentWith(it) } ?: signing?.value
    val spentEnergy = when {
        soldIsEnergy && !amountBlocked -> tx.spentAmount ?: sent?.amount ?: BigInteger.ZERO
        else -> BigInteger.ZERO
    }
    val nativeCovers = tx.fee.requiredEnergy?.let { it + spentEnergy <= energy.unitBalance.value } == true
    return when {
        selected != null && selected.isNative && spentEnergy.isPositive && !nativeCovers ->
            SwapFeeSummary.NoFunds(energy.asset.symbol)
        selected != null && TxFeeLogic.isSufficient(selected) -> selected.summary()
        selected != null && amountBlocked && selectedAccount?.asset?.id == tx.account.asset.id -> selected.summary()
        amountBlocked && soldIsEnergy && estimated != null -> estimated.summary()
        selected?.isBattery == true -> when {
            (selected.account.balance ?: BigInteger.ZERO) > BigInteger.ZERO -> SwapFeeSummary.LowBattery
            else -> SwapFeeSummary.BatteryEmpty
        }
        selected != null -> SwapFeeSummary.NoFunds(selectedAccount?.asset?.symbol ?: energy.asset.symbol)
        estimated == null -> SwapFeeSummary.Unknown
        nativeCovers -> estimated.summary()
        else -> SwapFeeSummary.NoFunds(energy.asset.symbol)
    }
}

fun swapSpentAmount(sellsCoin: Boolean, messagesAmount: BigInteger, sourceAmount: BigInteger): BigInteger? {
    if (sellsCoin) {
        return messagesAmount.takeIf { it > sourceAmount }
    }
    return sourceAmount
}

fun feeSummary(
    summary: SwapFeeSummary,
    noFunds: SwapFeeSummary.NoFunds,
    sendShortage: Boolean,
    balanceShort: Boolean,
): SwapFeeSummary = when {
    summary.isUnpaid -> summary
    sendShortage || balanceShort -> noFunds
    else -> summary
}

fun routeUsableMillis(lifetimeMillis: Long?): Long {
    if (lifetimeMillis == null) {
        return ROUTE_MAX_USABLE_MS
    }
    return (lifetimeMillis - ROUTE_EXPIRY_MARGIN_MS).coerceIn(ROUTE_MIN_USABLE_MS, ROUTE_MAX_USABLE_MS)
}

fun isRouteStale(
    elapsedNowMillis: Long,
    receivedAt: Long?,
    lifetimeMillis: Long?,
    quoteAgeMillis: Long,
): Boolean {
    val ageMillis = receivedAt?.let { elapsedNowMillis - it } ?: quoteAgeMillis
    return ageMillis >= routeUsableMillis(lifetimeMillis)
}

fun priceChange(snapshot: ConfirmType.Swap.Quote, sourceAmount: BigInteger, quote: SwapQuote): SwapPriceChange? {
    return when {
        quote.sourceBaseAmount > sourceAmount -> SwapPriceChange(
            side = SwapPriceChange.Side.Send,
            previous = sourceAmount,
            current = quote.sourceBaseAmount,
        )
        quote.buyBaseAmount < snapshot.buyAmount -> SwapPriceChange(
            side = SwapPriceChange.Side.Receive,
            previous = snapshot.buyAmount,
            current = quote.buyBaseAmount,
        )
        else -> null
    }
}

private fun TxFee.summary(): SwapFeeSummary {
    if (!isBattery) {
        return fee.summary()
    }
    return SwapFeeSummary.Charges(chargesAfterReserve.intValue(exactRequired = false))
}

private fun Fee.summary(): SwapFeeSummary.Amount {
    val display = asset.decimals.toDisplayUnit(amount)
    return SwapFeeSummary.Amount(Formatter.formatShort(value = display, asset = asset))
}
