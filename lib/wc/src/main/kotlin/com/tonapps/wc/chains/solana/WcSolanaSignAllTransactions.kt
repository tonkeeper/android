package com.tonapps.wc.chains.solana

import com.tonapps.wc.chains.WcRequestData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class WcSolanaSignAllTransactions(
    @SerialName("transactions")
    val transactions: List<String>,
) : WcRequestData
