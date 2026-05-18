package com.tonapps.wallet.data.tx.model

import com.tonapps.blockchain.ton.TonNetwork

data class PendingHash(
    val accountId: String,
    val network: TonNetwork,
    val hash: String
)