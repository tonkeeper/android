package com.tonapps.wc.chains.ethereum

import com.tonapps.wc.chains.WcRequestData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class WcEthTransaction(
    @SerialName("from")
    val from: String,
    @SerialName("data")
    val data: String,

    @SerialName("to")
    val to: String? = null,
    @SerialName("nonce")
    val nonce: String? = null,
    @SerialName("value")
    val value: String? = null,

    // STD
    @SerialName("gasLimit")
    val gasLimit: String? = null,
    @SerialName("gasPrice")
    val gasPrice: String? = null,

    @Transient
    val isSend: Boolean = false,
) : WcRequestData
