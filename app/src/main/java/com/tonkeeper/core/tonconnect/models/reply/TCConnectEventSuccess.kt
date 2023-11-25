package com.tonkeeper.core.tonconnect.models.reply

import org.json.JSONObject

data class TCConnectEventSuccess(
    val event: String = "connect",
    val id: Long = System.currentTimeMillis(),
    val payload: Payload,
): TCBase() {

    data class Payload(
        val items: List<TCReply> = mutableListOf(),
        val device: TCDevice = TCDevice(),
    ): TCBase() {
        override fun toJSON(): JSONObject {
            val json = JSONObject()
            json.put("items", TCReply.toJSONArray(items))
            json.put("device", device.toJSON())
            return json
        }
    }

    constructor(items: List<TCReply>): this(payload = Payload(items = items))

    override fun toJSON(): JSONObject {
        val json = JSONObject()
        json.put("event", event)
        json.put("id", id)
        json.put("payload", payload.toJSON())
        return json
    }
}
