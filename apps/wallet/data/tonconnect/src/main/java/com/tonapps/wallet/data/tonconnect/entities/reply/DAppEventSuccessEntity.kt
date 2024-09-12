package com.tonapps.wallet.data.tonconnect.entities.reply

import org.json.JSONObject

data class DAppEventSuccessEntity(
    val event: String = "connect",
    val id: Long = System.currentTimeMillis(),
    val payload: DAppReplyPayloadEntity,
): DAppReply() {

    constructor(
        items: List<DAppReply>,
        sendMaxMessages: Int
    ): this(
        payload = DAppReplyPayloadEntity(
            items = items,
            sendMaxMessages = sendMaxMessages
        )
    )

    override fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("event", event)
        json.put("id", id)
        json.put("payload", payload.toJSON())
        return json
    }

}