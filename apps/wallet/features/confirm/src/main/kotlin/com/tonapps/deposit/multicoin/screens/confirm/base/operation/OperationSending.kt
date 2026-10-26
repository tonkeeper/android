package com.tonapps.deposit.multicoin.screens.confirm.base.operation

import com.tonapps.blockchain.model.ConfirmRequest
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.account.CryptoWallet
import com.tonapps.chainkit.core.op.Payload
import com.tonapps.chainkit.core.op.SignedOperation
import com.tonapps.chainkit.core.op.SignedPayload
import com.tonapps.wallet.PendingSwap

class SentPayload(
    val payload: SignedPayload,
    val txHash: String,
)

fun interface OperationSender {
    /** Returns what this call broadcast: a payload an earlier call already sent is not returned again. */
    suspend fun send(walletId: String, account: Account, operation: SignedOperation): List<SentPayload>
}

/**
 * What a feature does around the send on top of the default pending report. Every call runs on the
 * send's own path: a throw fails the send.
 */
interface OperationTracker {

    /** Once per signed operation, before anything is broadcast, with the wallet still unlocked. */
    suspend fun onSigned(request: ConfirmRequest, account: Account, operation: SignedOperation, wallet: CryptoWallet) = Unit

    /** Wraps every send attempt of [operation]; [send] broadcasts and returns what went out. */
    suspend fun track(
        request: ConfirmRequest,
        account: Account,
        operation: SignedOperation,
        send: suspend () -> List<SentPayload>,
    ): List<SentPayload> = send()

    /** The pending report of [payload] as a swap; null reports it as what the payload itself is. */
    fun pendingSwap(request: ConfirmRequest, payload: Payload): PendingSwap? = null
}
