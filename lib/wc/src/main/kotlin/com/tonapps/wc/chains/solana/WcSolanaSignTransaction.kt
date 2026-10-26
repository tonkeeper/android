package com.tonapps.wc.chains.solana

import com.tonapps.wc.chains.WcRequestData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class WcSolanaSignTransaction(
    @SerialName("transaction")
    val transaction: String,

    @Transient
    val isSend: Boolean = false,
) : WcRequestData
