package com.tonapps.swap.screens.swap

import androidx.annotation.StringRes
import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.blockchain.model.ConfirmType
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.chainkit.core.chain.model.account.Asset
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.chainkit.core.chain.model.account.TokenType
import com.tonapps.deposit.multicoin.screens.confirm.SwapFeePlan
import com.tonapps.deposit.multicoin.screens.confirm.SwapFeeSummary
import com.tonapps.deposit.multicoin.screens.confirm.fee.GaslessRelayer
import com.tonapps.deposit.multicoin.screens.confirm.routeUsableMillis
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

sealed interface SwapFeeLine {
    data object Hidden : SwapFeeLine
    data object Promo : SwapFeeLine
    data object Fetching : SwapFeeLine
    data object Estimating : SwapFeeLine
    data object BareNetworkFee : SwapFeeLine
    data class Fee(val summary: SwapFeeSummary, val tappable: Boolean) : SwapFeeLine
    data class QuoteFailed(@StringRes val messageRes: Int) : SwapFeeLine
}

data class SwapBattery(
    val charges: Int,
    val relayedEvmChains: Set<String>,
)

internal val ConfirmRequest.quoteReceivedAt: Long?
    get() = (type as? ConfirmType.Swap)?.quote?.receivedAt

internal val SwapSend.quoteReceivedAt: Long?
    get() = request.quoteReceivedAt

fun feeLine(
    fetching: Boolean,
    quoteError: SwapQuoteError?,
    quote: DisplayQuote?,
    plan: SwapFeePlan?,
    promo: Boolean,
    amountEmpty: Boolean,
    tappable: Boolean,
): SwapFeeLine {
    val offline = quoteError is SwapQuoteError.Failed && quoteError.isOffline
    val keptOffline = offline && quote != null
    val settled = keptOffline || !fetching && quoteError == null && quote != null
    return when {
        amountEmpty && promo -> SwapFeeLine.Promo
        amountEmpty -> SwapFeeLine.Hidden
        !settled && fetching -> SwapFeeLine.Fetching
        !settled && offline -> SwapFeeLine.BareNetworkFee
        !settled && quoteError != null -> SwapFeeLine.QuoteFailed(quoteError.messageRes)
        !settled -> SwapFeeLine.Hidden
        plan is SwapFeePlan.Ready -> SwapFeeLine.Fee(plan.summary, tappable)
        plan == SwapFeePlan.Failed -> SwapFeeLine.Fee(SwapFeeSummary.Unknown, tappable)
        keptOffline -> SwapFeeLine.BareNetworkFee
        else -> SwapFeeLine.Estimating
    }
}

fun quoteLifetime(arrivalLifetimeMillis: Long?): Duration =
    routeUsableMillis(arrivalLifetimeMillis).milliseconds

fun showsBatteryPromo(
    shownBefore: Boolean,
    battery: SwapBattery?,
    sellAsset: Asset?,
    amountEmpty: Boolean,
    quoteIdle: Boolean,
): Boolean {
    if (shownBefore || battery == null || sellAsset == null || battery.charges > 0) {
        return false
    }
    return amountEmpty && quoteIdle && batteryCoversSwap(sellAsset, battery.relayedEvmChains)
}

fun batteryCoversSwap(asset: Asset, relayedEvmChains: Set<String>): Boolean {
    val chain = asset.chain
    return when {
        chain.network.mode != Network.Mode.Mainnet -> false
        chain.isTvm -> asset is Asset.Token
        chain is Chain.Tron -> asset is Asset.Coin || (asset as? Asset.Token)?.isTronUsdt == true
        chain.isEvm -> GaslessRelayer.chainId(chain) in relayedEvmChains
        else -> false
    }
}

private val Asset.Token.isTronUsdt: Boolean
    get() = type == TokenType.Trc20 && contract.display == TokenEntity.TRC20_USDT
