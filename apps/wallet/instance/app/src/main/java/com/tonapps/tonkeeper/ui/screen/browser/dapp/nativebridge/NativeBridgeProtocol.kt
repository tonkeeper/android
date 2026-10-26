package com.tonapps.tonkeeper.ui.screen.browser.dapp.nativebridge

import com.tonapps.async.Async
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.Protocol
import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.Script
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * `window.native`: `invoke(method, params)` resolves with the method's result or rejects with an
 * error that carries a `code`; `on(name, handler)` hears the events. [config] is read once per attach
 * and exposed as `window.native.config`.
 */
internal class NativeBridgeProtocol(
    private val config: suspend () -> JSONObject,
) : Protocol<NativeBridgeProtocol.Message, NativeBridgeProtocol.Response> {

    sealed interface Message {

        /** The script is installed in a new document, whose port events are then sent to. */
        data object Ready : Message

        class Request(val id: String, val method: String, val params: JSONObject) : Message
    }

    sealed interface Response {

        class Success(val id: String, val result: JSONObject) : Response

        class Failure(val id: String, val code: Int, val message: String) : Response

        class Event(val name: String, val payload: JSONObject) : Response
    }

    override val name: String = JS_INTERFACE

    override val script: Script = Script {
        withContext(Async.Io) { injectionScript(config()) }
    }

    override fun parse(data: String, host: String): Message? {
        val json = try {
            JSONObject(data)
        } catch (_: JSONException) {
            return null
        }
        if (json.optString("type") == TYPE_READY) {
            return Message.Ready
        }
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        val method = json.optString("method")
        val params = json.optJSONObject("params") ?: JSONObject()
        return Message.Request(id, method, params)
    }

    override fun encode(response: Response): String {
        val json = JSONObject()
        when (response) {
            is Response.Success -> json
                .put("id", response.id)
                .put("ok", true)
                .put("result", response.result)
            is Response.Failure -> json
                .put("id", response.id)
                .put("ok", false)
                .put("error", JSONObject().put("code", response.code).put("message", response.message))
            is Response.Event -> json
                .put("type", TYPE_EVENT)
                .put("event", response.name)
                .put("payload", response.payload)
        }
        return json.toString()
    }

    override fun route(message: Message): Protocol.Route {
        return when (message) {
            is Message.Ready -> Protocol.Route.Handshake
            is Message.Request -> Protocol.Route.Request(message.id)
        }
    }

    override fun replyId(response: Response): String? {
        return when (response) {
            is Response.Success -> response.id
            is Response.Failure -> response.id
            is Response.Event -> null
        }
    }

    override fun describeMessage(message: Message): String {
        return when (message) {
            is Message.Ready -> "ready"
            is Message.Request -> "${message.method} #${message.id}"
        }
    }

    override fun describeResponse(response: Response): String {
        return when (response) {
            is Response.Event -> "event ${response.name}"
            else -> super.describeResponse(response)
        }
    }

    private fun injectionScript(config: JSONObject): String {
        return """
            (() => {
                const channel = window.$JS_INTERFACE;
                // Only the main frame is answered: window.native in a frame would never be answered.
                if (window !== window.top || window.native || !channel) {
                    return;
                }
                const pending = {};
                // No prototype: a page-supplied name such as 'toString' must not hit Object's members.
                const listeners = Object.create(null);
                const emit = (name, payload) => {
                    if (name === '$EVENT_THEME_CHANGED' && payload) {
                        window.native.config.theme = payload.theme;
                    }
                    (listeners[name] || []).slice().forEach((handler) => {
                        try {
                            handler(payload);
                        } catch (e) {
                            console.error(e);
                        }
                    });
                };
                channel.addEventListener('message', (event) => {
                    let message;
                    try {
                        message = JSON.parse(event.data);
                    } catch (e) {
                        return;
                    }
                    if (message.type === '$TYPE_EVENT') {
                        emit(message.event, message.payload);
                        return;
                    }
                    const request = pending[message.id];
                    if (!request) {
                        return;
                    }
                    delete pending[message.id];
                    if (message.ok) {
                        request.resolve(message.result);
                    } else {
                        request.reject(Object.assign(new Error(message.error.message), { code: message.error.code }));
                    }
                });
                window.native = {
                    config: $config,
                    invoke: (method, params) => new Promise((resolve, reject) => {
                        const id = crypto.randomUUID();
                        pending[id] = { resolve, reject };
                        channel.postMessage(JSON.stringify({ id, method, params: params || {} }));
                    }),
                    on: (name, handler) => {
                        const handlers = listeners[name] || (listeners[name] = []);
                        handlers.push(handler);
                        return () => {
                            const index = handlers.indexOf(handler);
                            if (index > -1) {
                                handlers.splice(index, 1);
                            }
                        };
                    },
                };
                channel.postMessage(JSON.stringify({ type: '$TYPE_READY' }));
            })();
        """.trimIndent()
    }

    companion object {
        const val EVENT_THEME_CHANGED = "config.themeChanged"

        private const val JS_INTERFACE = "TonkeeperNative"
        private const val TYPE_READY = "ready"
        private const val TYPE_EVENT = "event"
    }
}