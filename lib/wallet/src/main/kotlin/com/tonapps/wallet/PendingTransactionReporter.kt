package com.tonapps.wallet

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.account.Account
import com.tonapps.chainkit.core.chain.model.transaction.Transaction
import com.tonapps.chainkit.core.op.Payload

interface PendingTransactionReporter {
    suspend fun onTransactionSent(
        walletId: String,
        tx: Transaction,
        txHash: String,
        service: String?,
        routeId: String?,
        serviceTxId: String?,
        sellBaseAmount: BigInteger?,
    )

    /** [swap] reports the payload as a swap; without it the activity is read off the payload. */
    suspend fun onPayloadSent(
        walletId: String,
        account: Account,
        payload: Payload,
        txHash: String,
        swap: PendingSwap? = null,
    )
}

class PendingSwap(
    val destinationAssetId: String,
    val service: String?,
    val routeId: String?,
    val serviceTxId: String?,
    val sellBaseAmount: BigInteger?,
)
