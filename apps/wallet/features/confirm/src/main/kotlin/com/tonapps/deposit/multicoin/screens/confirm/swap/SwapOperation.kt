package com.tonapps.deposit.multicoin.screens.confirm

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.blockchain.model.ConfirmOperations
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.Address
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.common.getOrElse
import com.tonapps.chainkit.core.op.Operation
import com.tonapps.chainkit.core.op.PayloadMeta
import com.tonapps.wallet.data.multichain.exchange.SwapPayloadInfo
import com.tonapps.wallet.data.multichain.exchange.SwapRoute

/**
 * A swap route's transactions: [approvals] go out ahead of [messages] as transactions of their own,
 * and [messages] are the swap itself — more than one only for a split TON route.
 */
internal class SwapTransactions(
    val approvals: List<Transaction>,
    val messages: List<Transaction.Swap>,
)

/**
 * The route as an operation: the approve first, when the route needs one, then the swap. Only an
 * EVM approve is kept, as its own transaction ahead of the swap.
 */
internal fun SwapRoute.toOperation(chain: Chain): Operation {
    val approve = approval?.takeIf { chain.isEvm }?.let {
        val meta = PayloadMeta.Evm.Main(to = Address.force(it.to, chain), value = it.amount, data = it.data)
        ConfirmOperations.payload(0, chain, meta, it.fee)
    }

    // The route quotes a fee for a single swap message alone.
    val fee = mainMessages.singleOrNull()?.fee
    val offset = if (approve == null) {
        0
    } else {
        1
    }
    val swap = mainMessages.mapIndexed { index, message ->
        ConfirmOperations.payload(offset + index, chain, message.toMeta(chain), fee)
    }
    return ConfirmOperations.build(listOfNotNull(approve) + swap)
}

/**
 * Binds [operation] to the sender: the payloads ahead of [messages] are its approvals. The swap is
 * bound from [messages] themselves, which keep an exact payload's prebuilt data (a BTC PSBT, a raw
 * Tron transaction) that no main payload meta carries.
 */
internal fun swapTransactions(
    operation: Operation,
    messages: List<SwapPayloadInfo.Data>,
    account: Account,
    destination: Account,
    isMax: Boolean,
): SwapTransactions {
    val approvals = operation.payloads.take(operation.payloads.size - messages.size).map { payload ->
        payload.toTransaction(account).getOrElse { throw it }
    }
    return SwapTransactions(
        approvals = approvals,
        messages = messages.map { message ->
            swap(
                account = account,
                destination = destination,
                isMax = isMax,
                to = Address.force(message.to, account.chain),
                amount = message.amount,
                data = message.data,
                memo = message.memo,
            )
        },
    )
}

private fun SwapPayloadInfo.Data.toMeta(chain: Chain): PayloadMeta {
    val to = Address.force(to, chain)
    return when {
        chain.isEvm -> PayloadMeta.Evm.Main(to = to, value = amount, data = data)
        chain.isTvm -> PayloadMeta.Ton.Main(to = to, value = amount, body = data, mode = mode)
        chain is Chain.Tron -> PayloadMeta.Tron.Main(to = to, value = amount, data = data, memo = memo)
        chain is Chain.Bitcoin -> PayloadMeta.Bitcoin.Main(to = to, value = amount, memo = memo)
        chain is Chain.Solana -> PayloadMeta.Solana.Main(to = to, value = amount, memo = memo)
        else -> throw IllegalArgumentException("Swaps on ${chain.caip2} have no operation payload")
    }
}

private fun swap(
    account: Account,
    destination: Account,
    isMax: Boolean,
    to: Address,
    amount: BigInteger,
    data: String? = null,
    memo: String? = null,
) = Transaction.Swap(
    account = account,
    amount = amount,
    destination = destination,
    to = to,
    isMax = isMax,
    data = data,
    memo = memo,
)
