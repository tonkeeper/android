package com.tonapps.wallet.data.multichain.lighter

data class SignedLighterTransaction(
    val txType: Int,
    val txInfo: String,
    val txHash: String,
    val nonce: Long,
    val expiredAtMs: Long,
)
