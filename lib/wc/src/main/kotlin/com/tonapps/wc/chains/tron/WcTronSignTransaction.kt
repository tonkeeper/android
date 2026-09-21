package com.tonapps.wc.chains.tron

import com.tonapps.wc.chains.WcRequestData
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
class WcTronSignTransaction(
    @SerialName("transaction")
    val transaction: WrapTransaction,
) : WcRequestData {
    @Serializable
    data class WrapTransaction(
        @SerialName("transaction")
        val transaction: JsonObject,
    )
}
