package com.tonapps.wallet.data.multichain.exchange.status

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.num.Formatter
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.data.multichain.account.toApiChain
import com.tonapps.wallet.data.multichain.account.toAssetEntity
import com.tonapps.wallet.data.multichain.activity.TronResourceEntity
import com.tonapps.wallet.data.multichain.activity.batteryChargesOrNull
import com.tonapps.wallet.data.multichain.activity.isBatteryFee
import com.tonapps.wallet.data.multichain.activity.tronResourceOrNull
import com.tonapps.wallet.data.multichain.asset.AssetEntity
import io.walletapi.models.Activity
import io.walletapi.models.ActivityExplorerLink

data class SwapDetails(
    val swap: SentSwap,
    val status: SwapStatus,
    val sendAmount: String,
    val receiveAmount: String,
    val date: Long,
    val sent: ActivityExplorerLink?,
    val received: ActivityExplorerLink?,
    val receiving: Boolean,
    val networkFee: SwapNetworkFee?,
)

sealed interface SwapNetworkFee {
    data class Charges(val count: Int) : SwapNetworkFee
    data class Amount(val text: String) : SwapNetworkFee
    data class TronResource(val resource: TronResourceEntity) : SwapNetworkFee
}

internal fun formatSwapAmount(asset: AssetEntity, amount: BigInteger, hidden: Boolean): String = if (hidden) {
    HIDDEN_BALANCE
} else {
    Formatter.formatShort(
        value = asset.value.decimals.baseUnit(amount).toDisplayUnit(),
        asset = asset.value,
        options = Formatter.defaultOptions.copy(symbolOverride = asset.symbol.ifEmpty { asset.name }),
    )
}

internal fun String.unsigned() = trim().trimStart('-', '+', '−').toBigIntegerOrNull()

private fun networkFee(activity: Activity, hidden: Boolean): SwapNetworkFee? {
    if (activity.isBatteryFee()) {
        val charges = activity.batteryChargesOrNull() ?: return null
        return when (hidden) {
            true -> SwapNetworkFee.Amount(HIDDEN_BALANCE)
            false -> SwapNetworkFee.Charges(charges)
        }
    }
    val units = (activity.fee?.amount ?: activity.feeAmount)?.unsigned()
    val resource = activity.tronResourceOrNull()
    if (resource != null && (units == null || units.signum() == 0)) {
        return SwapNetworkFee.TronResource(resource)
    }
    val token = (activity.fee?.token ?: activity.feeToken)
        ?.toAssetEntity()
        ?.takeIf { it.valueOrNull != null }
        ?: return null
    units ?: return null
    return SwapNetworkFee.Amount(formatSwapAmount(token, BigInteger.parseString(units.toString()), hidden))
}

internal fun swapDetails(swap: SentSwap, status: SwapStatus, activity: Activity?, sentAt: Long, hidden: Boolean): SwapDetails {
    val links = activity?.explorerUrls.orEmpty()
    val sent = links.firstOrNull { it.chain.value == activity?.fromChain }
    val received = links.firstOrNull { it.chain.value == activity?.toChain && it != sent }
    val crossChain = swap.sell.valueOrNull?.chain?.toApiChain() != swap.buy.valueOrNull?.chain?.toApiChain()
    val actualReceive = activity?.inAmount
        ?.takeIf { status == SwapStatus.Done && activity.inToken?.assetId == swap.buy.id }
        ?.unsigned()
        ?.let { BigInteger.parseString(it.toString()) }
    return SwapDetails(
        swap = swap,
        status = status,
        sendAmount = formatSwapAmount(swap.sell, swap.sendAmount, hidden),
        receiveAmount = formatSwapAmount(swap.buy, actualReceive ?: swap.receiveAmount, hidden),
        date = activity?.blockTime?.toInstant()?.toEpochMilli() ?: sentAt,
        sent = sent,
        received = received.takeIf { crossChain && status != SwapStatus.Failed },
        receiving = crossChain && status == SwapStatus.Pending && sent != null && received == null,
        networkFee = activity?.let { networkFee(it, hidden) },
    )
}
