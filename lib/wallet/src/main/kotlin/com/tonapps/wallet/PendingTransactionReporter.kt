package com.tonapps.wallet

import com.ionspin.kotlin.bignum.integer.BigInteger
import com.tonapps.chainkit.core.chain.model.transaction.Transaction

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
}
