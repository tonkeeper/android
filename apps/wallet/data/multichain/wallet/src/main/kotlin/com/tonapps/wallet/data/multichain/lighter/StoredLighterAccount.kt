package com.tonapps.wallet.data.multichain.lighter

/** The venue account and API-key slot this wallet's L2 key is registered against. */
data class StoredLighterAccount(
    val accountIndex: Long,
    val apiKeyIndex: Int,
)
