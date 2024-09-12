package com.tonapps.wallet.data.tonconnect.entities.reply

import org.json.JSONObject

data class DAppReplyPayloadEntity(
    val items: List<DAppReply> = mutableListOf(),
    val device: DAppDeviceEntity,
): DAppReply() {

    constructor(
        items: List<DAppReply>,
        sendMaxMessages: Int
    ) : this(
        items = items,
        device = DAppDeviceEntity(sendMaxMessages = sendMaxMessages)
    )

    override fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("items", toJSONArray(items))
        json.put("device", device.toJSON())
        return json
    }
}