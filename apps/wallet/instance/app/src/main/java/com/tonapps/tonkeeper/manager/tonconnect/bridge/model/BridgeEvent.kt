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

    sealed interface Event

    val method: BridgeMethod
        get() = message.method


    companion object {
        fun parse(array: JSONArray): List<Message> {
            val messages = mutableListOf<Message>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                when {
//                obj.has("traceId") -> messages.add(Trace(obj))
                    obj.has("method") -> messages.add(Message(obj))
                }
            }

            return messages
        }
    }

    data class Trace(
        val traceId: String,
    ) : Event {
        constructor(json: JSONObject) : this(
            json.getString("traceId"),
        )
    }

    data class Message(
        val method: BridgeMethod,
        val params: List<String>,
        val id: Long,
    ) : Event {

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
        }
    }
}
