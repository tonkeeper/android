package com.tonapps.wc.chains.tron

import com.tonapps.wc.chains.WcRequestData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class WcTronSignMessage(
    @SerialName("message")
    val message: String,
) : WcRequestData
