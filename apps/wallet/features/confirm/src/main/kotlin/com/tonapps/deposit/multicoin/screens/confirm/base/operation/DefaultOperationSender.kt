package com.tonapps.deposit.multicoin.screens.confirm.base.operation

import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.op.SignedOperation
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.wallet.ChainKitProvider
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/** Broadcasts every signed payload to its chain through chainkit, in the operation's order. */
class DefaultOperationSender(
    private val provider: ChainKitProvider,
) : OperationSender {

    // A retry hands the whole operation over again: what a chain already took is not sent twice.
    private val sent: MutableSet<Pair<String, String>> = ConcurrentHashMap.newKeySet()

    override suspend fun send(walletId: String, account: Account, operation: SignedOperation): List<SentPayload> {
        // Checked up front: an operation with a payload that cannot be broadcast sends nothing at all.
        operation.payloads.forEach { it.requireTransaction() }
        val broadcast = ArrayList<SentPayload>()
        for (payload in operation.payloads) {
            val key = operation.id to payload.payloadId
            if (key in sent) {
                continue
            }

            val result = provider.broadcastPayload(payload, account)
            val error = result.error
            if (error != null) {
                throw error
            }

            sent += key
            broadcast += SentPayload(payload = payload, txHash = result.unwrap())
        }
        return broadcast
    }

    private fun SignedPayload.requireTransaction() {
        require(type.isTransaction && (data as? JsonPrimitive)?.isString == true) {
            "Payload $payloadId is signed into `${type.id}`, which no chain takes as a transaction"
        }
    }
}
