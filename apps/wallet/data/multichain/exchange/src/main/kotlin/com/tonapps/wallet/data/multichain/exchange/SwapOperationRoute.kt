package com.tonapps.wallet.data.multichain.exchange

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.common.getOrElse
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.PayloadMeta
import io.exchangeapi.models.CrossSwapCalldataPayloadType

fun PreparedSwapOperation.toSwapRoute(account: Account, spendAmount: BigInteger): SwapRoute {
    val chain = account.chain
    val prebuiltTo = depositAddress ?: account.addressDisplay
    val messages = operation.payloads.flatMap { it.toSwapMessages(account, prebuiltTo, spendAmount) }
    if (messages.isEmpty()) {
        throw SwapRouteRefusedException("Operation ${operation.id} has no messages")
    }
    if (chain.isTvm && messages.size > 1) {
        throw SwapRouteRefusedException("Operation ${operation.id} sends ${messages.size} TON messages, a confirmation each, longer than the route lasts")
    }

    val approval = if (chain.isEvm && messages.size > 1) {
        messages.first().toApproval()
    } else {
        null
    }
    val mainMessages = if (approval == null) {
        messages
    } else {
        messages.drop(1)
    }
    if (chain.isEvm && mainMessages.size != 1) {
        throw SwapRouteRefusedException("Operation ${operation.id} makes ${mainMessages.size} EVM calls besides an approve")
    }

    return SwapRoute(
        mainMessages = mainMessages,
        approval = approval,
        providerRouteId = providerRouteId,
        buyBaseAmount = buyBaseAmount,
        minimumBuyBaseAmount = minimumBuyBaseAmount,
    )
}

private fun Payload.toSwapMessages(account: Account, prebuiltTo: String, spendAmount: BigInteger): List<SwapPayloadInfo.Data> {
    val messages = when (val meta = meta) {
        is PayloadMeta.Evm.Main -> {
            if (meta.authorization != null) {
                throw unsupported()
            }
            listOf(message(meta.to.display, meta.value, meta.data))
        }
        is PayloadMeta.Ton.Main -> if (meta.token == null) {
            listOf(meta.toMessage())
        } else {
            listOf(jettonDeposit(account))
        }
        is PayloadMeta.Ton.Batch -> meta.calls.map { call ->
            (call as? PayloadMeta.Ton.Main)?.toMessage() ?: throw unsupported()
        }
        is PayloadMeta.Tron.Main -> listOf(message(meta.to.display, meta.value, meta.data, memo = meta.memo))
        is PayloadMeta.Tron.Prebuild -> listOf(message(prebuiltTo, spendAmount, meta.data))
        is PayloadMeta.Solana.Main -> listOf(message(meta.to.display, meta.value, data = null, memo = meta.memo))
        is PayloadMeta.Solana.Prebuild -> listOf(message(prebuiltTo, spendAmount, meta.data))
        is PayloadMeta.Bitcoin.Main -> listOf(message(meta.to.display, meta.value, data = null, memo = meta.memo))
        else -> throw unsupported()
    }
    return messages.map { it.copy(fee = fee) }
}

private fun Payload.unsupported() = SwapRouteRefusedException("A ${type.id} payload on ${chain.caip2} is not signed on this route")

private fun Payload.jettonDeposit(account: Account): SwapPayloadInfo.Data {
    val transfer = toTransaction(account).getOrElse {
        throw SwapRouteRefusedException("A TON jetton deposit is not signed from ${account.asset.id}: ${it.message}")
    } as? Transaction.Transfer ?: throw unsupported()
    if (transfer.memo != null) {
        throw SwapRouteRefusedException("A TON jetton deposit with a comment is not signed on this route")
    }
    return message(transfer.to.display, transfer.amount, data = null)
}

private fun PayloadMeta.Ton.Main.toMessage(): SwapPayloadInfo.Data {
    if (stateInit != null || token != null || comment != null) {
        throw SwapRouteRefusedException("A TON message with a state init, a jetton or a comment is not signed on this route")
    }
    return message(to.display, value, body, mode = mode)
}

private fun message(
    to: String,
    amount: BigInteger,
    data: String?,
    mode: Int? = null,
    memo: String? = null,
) = SwapPayloadInfo.Data(
    to = to,
    amount = amount,
    data = data?.takeIf { it.isNotBlank() },
    calldataType = CrossSwapCalldataPayloadType.exact,
    mode = mode,
    memo = memo?.takeIf { it.isNotBlank() },
)

private fun SwapPayloadInfo.Data.toApproval(): SwapPayloadInfo.Approve {
    val calldata = data?.takeIf { it.removePrefix("0x").startsWith(APPROVE_SELECTOR, ignoreCase = true) }
        ?: throw SwapRouteRefusedException("The call ahead of the swap is not an approve")
    return SwapPayloadInfo.Approve(
        to = to,
        amount = amount,
        data = calldata,
        fee = fee,
        calldataType = calldataType,
    )
}

private const val APPROVE_SELECTOR = "095ea7b3"
