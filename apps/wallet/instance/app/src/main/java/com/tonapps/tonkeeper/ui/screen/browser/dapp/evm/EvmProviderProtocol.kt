package com.tonapps.tonkeeper.ui.screen.browser.dapp.evm

import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.Protocol
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.Script
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The other half lives in tools/scripts/generators/evm-provider; its README defines this wire format.
 */
internal class EvmProviderProtocol(
    override val script: Script,
) : Protocol<EvmProviderProtocol.Message, EvmProviderProtocol.Response> {

    override val name: String = JS_INTERFACE

    sealed interface Message {

        data object Handshake : Message

        data class Request(
            val id: Long,
            val method: String,
            val params: List<Any?>,
            val host: String = "",
        ) : Message
    }

    /**
     * [requestId] names the request an answer belongs to, so it can be delivered to the frame that
     * asked. A null id means the answer belongs to the session rather than to a single request.
     */
    sealed interface Response {

        val requestId: Long?

        data class Connected(val address: String, val chainId: String) : Response {
            override val requestId: Long? = null
        }

        data class Unavailable(val code: Int, val message: String) : Response {
            override val requestId: Long? = null
        }

        data class Result(override val requestId: Long, val value: Any?) : Response

        data class Failure(
            override val requestId: Long,
            val code: Int,
            val message: String,
        ) : Response

        data class ChainSwitched(override val requestId: Long, val chainId: String) : Response
    }

    override fun encode(response: Response): String {
        val json = JSONObject()
        when (response) {
            is Response.Connected -> json
                .put("type", "config")
                .put("address", response.address)
                .put("chainId", response.chainId)
            is Response.Unavailable -> json
                .put("type", "unavailable")
                .put("code", response.code)
                .put("message", response.message)
            is Response.Result -> json
                .put("type", "result")
                .put("id", response.requestId)
                .put("value", toJson(response.value))
            is Response.Failure -> json
                .put("type", "error")
                .put("id", response.requestId)
                .put("code", response.code)
                .put("message", response.message)
            is Response.ChainSwitched -> json
                .put("type", "chainSwitched")
                .put("id", response.requestId)
                .put("chainId", response.chainId)
        }
        return json.toString()
    }

    override fun parse(data: String, host: String): Message? {
        val json = try {
            JSONObject(data)
        } catch (_: JSONException) {
            return null
        }
        val method = json.optString("method").takeIf { it.isNotBlank() } ?: return null
        if (method == HANDSHAKE) {
            return Message.Handshake
        }
        val id = json.optLong("id", -1L).takeIf { it >= 0 } ?: return null
        return Message.Request(id, method, params(json), host)
    }

    override fun route(message: Message): Protocol.Route {
        return when (message) {
            is Message.Handshake -> Protocol.Route.Handshake
            is Message.Request -> Protocol.Route.Request(message.id.toString())
        }
    }

    override fun replyId(response: Response): String? = response.requestId?.toString()

    override fun describeMessage(message: Message): String {
        return when (message) {
            is Message.Handshake -> "handshake"
            is Message.Request -> "request ${message.method} #${message.id}"
        }
    }

    private fun params(json: JSONObject): List<Any?> {
        val array = json.optJSONArray("params")
            ?: json.optJSONObject("params")?.let { JSONArray().put(it) }
            ?: return emptyList()
        return (0 until array.length()).map { decode(array.opt(it)) }
    }

    private fun decode(value: Any?): Any? {
        return when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> value.keys().asSequence().associateWith { decode(value.opt(it)) }
            is JSONArray -> (0 until value.length()).map { decode(value.opt(it)) }
            else -> value
        }
    }

    private fun toJson(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is List<*> -> JSONArray().apply { value.forEach { put(toJson(it)) } }
            is Map<*, *> -> JSONObject().apply {
                value.forEach { (key, item) -> put(key.toString(), toJson(item)) }
            }
            else -> value
        }
    }

    companion object {

        private const val JS_INTERFACE = "TonkeeperEvm"

        private const val HANDSHAKE = "wallet_provider_ready"
    }
}
