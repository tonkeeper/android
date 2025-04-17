package com.tonapps.tonkeeper.manager.tonconnect.bridge.model

import com.tonapps.extensions.getLongCompat
import com.tonapps.extensions.toStringList
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import org.json.JSONArray
import org.json.JSONObject

data class BridgeEvent(
    val eventId: Long,
    val message: Message,
    val connection: AppConnectEntity,
) {

    val method: BridgeMethod
        get() = message.method

    data class Message(
        val method: BridgeMethod,
        val params: List<String>,
        val id: Long,
    ) {

        constructor(json: JSONObject) : this(
            BridgeMethod.of(json.getString("method")),
            smartParseParams(json),
            json.getLongCompat("id"),
        )

        companion object {

            fun smartParseParams(json: JSONObject): List<String> {
                return when (val params = json.get("params")) {
                    is JSONArray -> params.toStringList()
                    is String -> listOf(params)
                    else -> listOf(params.toString())
                }
            }

            fun parse(array: JSONArray): List<Message> {
                val messages = mutableListOf<Message>()
                for (i in 0 until array.length()) {
                    messages.add(Message(array.getJSONObject(i)))
                }
                return messages
            }
        }
    }
}