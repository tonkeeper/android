package com.tonapps.wallet.data.multichain.lighter

import com.tonapps.chainkit.perpetual.PerpsSigningKey

data class LighterSigningCredentials(
    val key: PerpsSigningKey,
    val accountIndex: Long,
    val apiKeyIndex: Int,
    val chainId: Int,
    val environment: String,
)
