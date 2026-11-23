package com.tonapps.deposit.multicoin.screens.confirm

import android.os.SystemClock
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.exchange.SwapNoRouteReason
import com.tonapps.wallet.data.multichain.exchange.SwapQuote
import com.tonapps.wallet.data.multichain.exchange.SwapQuoteResult
import com.tonapps.wallet.data.multichain.exchange.SwapRepository
import com.tonapps.wallet.data.multichain.exchange.SwapWalletScreenedException

private const val BPS_IN_WHOLE = 10_000

/** The quote the swap screen handed over, as the route it names; null when it handed none. */
internal fun ConfirmType.Swap.snapshotQuote(): SwapQuote? {
    val snapshot = quote ?: return null
    return SwapQuote(
        routeId = snapshot.routeId,
        sourceBaseAmount = sourceAmount,
        buyBaseAmount = snapshot.buyAmount,
        minimumBuyBaseAmount = snapshot.minBuyAmount,
        slippageBps = snapshot.slippageBps,
        priceImpactBps = snapshot.priceImpactBps,
        provider = SwapQuote.Provider.forceFrom(snapshot.provider),
        providerTxId = snapshot.providerTxId,
        receivedAt = snapshot.receivedAt,
        lifetimeMillis = snapshot.lifetimeMillis,
        genericPayload = snapshot.genericPayload,
    )
}

/** This quote as the request carries it. */
internal fun SwapQuote.toSnapshot() = ConfirmType.Swap.Quote(
    routeId = routeId,
    buyAmount = buyBaseAmount,
    minBuyAmount = minimumBuyBaseAmount,
    provider = provider.id,
    providerTxId = providerTxId,
    slippageBps = slippageBps,
    priceImpactBps = priceImpactBps,
    receivedAt = receivedAt,
    lifetimeMillis = lifetimeMillis,
    genericPayload = genericPayload,
)

/**
 * A fresh quote of [amount] of [sell] for [buy], on `/v3` when [genericPayload], stamped with when it
 * came in and how long it lives. A wallet the provider screened is a [SwapWalletScreenedException]; no
 * route at all, an error to retry.
 */
internal suspend fun SwapRepository.fetchConfirmQuote(
    sell: AccountWithDetails,
    buy: AccountWithDetails,
    amount: BigInteger,
    slippageBps: Int?,
    walletId: String?,
    genericPayload: Boolean,
): SwapQuote {
    val result = fetchQuote(
        sell = sell,
        buy = buy,
        amount = amount,
        slippageBps = slippageBps,
        walletId = walletId,
        genericPayload = genericPayload,
    )
    return when (result) {
        is SwapQuoteResult.Found -> result.quote.copy(
            receivedAt = SystemClock.elapsedRealtime(),
            lifetimeMillis = result.quote.expiresAtMillis?.let { it - System.currentTimeMillis() },
        )
        is SwapQuoteResult.NoRoute -> if (result.reason == SwapNoRouteReason.WalletScreened) {
            throw SwapWalletScreenedException()
        } else {
            throw IllegalStateException("No swap route available")
        }
    }
}

/** The quote's minimum, scaled to what the prepared route says it receives. */
internal fun SwapQuote.minimumBuyBaseAmountAt(receiveBaseAmount: BigInteger): BigInteger {
    if (!buyBaseAmount.isPositive) {
        return minimumBuyBaseAmount
    }
    return minimumBuyBaseAmount * receiveBaseAmount / buyBaseAmount
}

/** The quote's price impact, moved by what the prepared route says it receives. */
internal fun SwapQuote.priceImpactBpsAt(receiveBaseAmount: BigInteger): Int? {
    val bps = priceImpactBps ?: return null
    if (!buyBaseAmount.isPositive) {
        return bps
    }
    return (BigInteger.fromInt(BPS_IN_WHOLE + bps) * receiveBaseAmount / buyBaseAmount).intValue(exactRequired = false) - BPS_IN_WHOLE
}

/**
 * The quote with what the prepared route states: its provider id, and what it receives, at least —
 * never more than it receives.
 */
internal fun SwapQuote.preparedAs(providerTxId: String?, buyBaseAmount: BigInteger?, minimumBuyBaseAmount: BigInteger?): SwapQuote {
    val receives = buyBaseAmount ?: this.buyBaseAmount
    return copy(
        providerTxId = providerTxId ?: this.providerTxId,
        buyBaseAmount = receives,
        minimumBuyBaseAmount = minOf(minimumBuyBaseAmount ?: minimumBuyBaseAmountAt(receives), receives),
        priceImpactBps = priceImpactBpsAt(receives),
    )
}
