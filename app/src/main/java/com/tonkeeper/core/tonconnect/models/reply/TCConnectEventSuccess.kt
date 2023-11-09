package com.tonkeeper.core.tonconnect.models.reply

import com.tonkeeper.core.tonconnect.models.TCDevice
import kotlinx.serialization.Serializable

@Serializable
data class TCConnectEventSuccess(
    val event: String = "connect",
    val id: Long = System.currentTimeMillis(),
    val payload: Payload,
): TCReply() {

    @Serializable
    data class Payload(
        val items: List<TCReply> = mutableListOf(),
        val device: TCDevice = TCDevice(),
    )

    constructor(items: List<TCReply>): this(payload = Payload(items = items))
}
