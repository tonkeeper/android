package com.tonapps.tonkeeper.ui.screen.browser.dapp.keeper

import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppPageEvent
import com.tonapps.webview.bridge.InAppBridgeContract.HapticStyle
import com.tonapps.webview.bridge.InAppBridgeContract.Protocol
import com.tonapps.webview.bridge.InAppBridgeContract.Script
import org.json.JSONException
import org.json.JSONObject

internal const val KEEPER_BRIDGE_CHANNEL = "ReactNativeWebView"

internal class KeeperBridgeProtocol : Protocol<KeeperBridgeProtocol.Request, KeeperBridgeProtocol.Response> {

    sealed interface Request {

        val queryId: String

        class GetData(override val queryId: String, val host: String) : Request

        class RefreshData(override val queryId: String, val expiredAccessToken: String) : Request

        class Close(override val queryId: String) : Request

        class Haptic(override val queryId: String, val style: HapticStyle) : Request

        class SetCloseButton(override val queryId: String, val visible: Boolean) : Request

        class Share(override val queryId: String, val text: String) : Request

        class AnalyticsTrack(
            override val queryId: String,
            val event: String,
            val params: Map<String, Any?>,
            val provider: String,
        ) : Request

        class Rejected(override val queryId: String, val error: ErrorCode) : Request
    }

    sealed interface Response {

        val queryId: String

        class Data(
            override val queryId: String,
            val credentials: Credentials,
            val appSettings: AppSettings?,
            val referralCode: String? = null,
        ) : Response

        class Done(override val queryId: String) : Response

        class Failure(override val queryId: String, val error: ErrorCode) : Response
    }

    class Credentials(
        val walletId: String,
        val deviceToken: String,
        val walletToken: String,
    )

    class AppSettings(
        val theme: String,
        val language: String,
    )

    enum class ErrorCode(val code: String, val message: String) {
        Unauthorized("UNAUTHORIZED", "Authorization failed"),
        UnsupportedMethod("UNSUPPORTED_METHOD", "Unsupported method"),
        InvalidParams("INVALID_PARAMS", "Invalid params"),
        Forbidden("FORBIDDEN", "Forbidden"),
    }

    override val name: String = KEEPER_BRIDGE_CHANNEL

    override val script: Script = Script { INJECTION_SCRIPT }

    override fun parse(data: String, host: String, pageUrl: String?): Request? {
        val json = try {
            JSONObject(data)
        } catch (_: JSONException) {
            return null
        }
        val queryId = json.string(KEY_QUERY_ID) ?: return null
        if (!isKeeperDAppHost(host)) {
            return Request.Rejected(queryId, ErrorCode.Forbidden)
        }
        val request = when (json.string(KEY_TYPE)) {
            null -> null
            TYPE_GET_DATA -> Request.GetData(queryId, host)
            TYPE_REFRESH_DATA -> json.string(KEY_ACCESS_TOKEN)?.let { Request.RefreshData(queryId, it) }
            TYPE_CLOSE -> Request.Close(queryId)
            TYPE_HAPTIC -> hapticStyle(json.string(KEY_STYLE))?.let { Request.Haptic(queryId, it) }
            TYPE_SET_CLOSE_BUTTON -> (json.opt(KEY_VISIBLE) as? Boolean)?.let { Request.SetCloseButton(queryId, it) }
            TYPE_SHARE -> json.string(KEY_TEXT)?.let { Request.Share(queryId, it) }
            TYPE_ANALYTICS_TRACK -> json.string(KEY_EVENT)?.let { event ->
                Request.AnalyticsTrack(queryId, event, DAppPageEvent.rawParams(json.optJSONObject(KEY_PARAMS)), host.lowercase())
            }
            else -> return Request.Rejected(queryId, ErrorCode.UnsupportedMethod)
        }
        return request ?: Request.Rejected(queryId, ErrorCode.InvalidParams)
    }

    override fun encode(response: Response): String {
        val json = JSONObject().put(KEY_QUERY_ID, response.queryId)
        when (response) {
            is Response.Data -> json
                .put(KEY_PAYLOAD, response.payload())
                .put(KEY_ERROR, JSONObject.NULL)
            is Response.Done -> json
                .put(KEY_PAYLOAD, JSONObject())
                .put(KEY_ERROR, JSONObject.NULL)
            is Response.Failure -> json
                .put(KEY_PAYLOAD, JSONObject.NULL)
                .put(KEY_ERROR, JSONObject().put("code", response.error.code).put("message", response.error.message))
        }
        return json.toString()
    }

    override fun route(message: Request): Protocol.Route = Protocol.Route.Request(message.queryId)

    override fun replyId(response: Response): String = response.queryId

    override fun describeMessage(message: Request): String {
        val method = when (message) {
            is Request.GetData -> TYPE_GET_DATA
            is Request.RefreshData -> TYPE_REFRESH_DATA
            is Request.Close -> TYPE_CLOSE
            is Request.Haptic -> TYPE_HAPTIC
            is Request.SetCloseButton -> TYPE_SET_CLOSE_BUTTON
            is Request.Share -> TYPE_SHARE
            is Request.AnalyticsTrack -> TYPE_ANALYTICS_TRACK
            is Request.Rejected -> message.error.code
        }
        return "$method #${message.queryId}"
    }

    private fun Response.Data.payload(): JSONObject {
        val payload = JSONObject()
            .put("walletId", credentials.walletId)
            .put("deviceToken", credentials.deviceToken)
            .put("walletToken", credentials.walletToken)
        appSettings?.let { settings ->
            payload.put("theme", settings.theme).put("language", settings.language)
        }
        referralCode?.let { payload.put("referralCode", it) }
        return payload
    }

    private fun hapticStyle(style: String?): HapticStyle? {
        return when (style) {
            "selection" -> HapticStyle.Selection
            "light" -> HapticStyle.Light
            "medium" -> HapticStyle.Medium
            "heavy" -> HapticStyle.Heavy
            "success" -> HapticStyle.Success
            "warning" -> HapticStyle.Warning
            "error" -> HapticStyle.Error
            else -> null
        }
    }

    private fun JSONObject.string(key: String): String? {
        return (opt(key) as? String)?.takeIf { it.isNotBlank() }
    }

    private companion object {
        private const val KEY_QUERY_ID = "queryId"
        private const val KEY_TYPE = "type"
        private const val KEY_ACCESS_TOKEN = "accessToken"
        private const val KEY_STYLE = "style"
        private const val KEY_VISIBLE = "visible"
        private const val KEY_TEXT = "text"
        private const val KEY_EVENT = "event"
        private const val KEY_PARAMS = "params"
        private const val KEY_PAYLOAD = "payload"
        private const val KEY_ERROR = "error"

        private const val TYPE_GET_DATA = "get-data"
        private const val TYPE_REFRESH_DATA = "refresh-data"
        private const val TYPE_CLOSE = "close"
        private const val TYPE_HAPTIC = "haptic"
        private const val TYPE_SET_CLOSE_BUTTON = "set-close-button"
        private const val TYPE_SHARE = "share"
        private const val TYPE_ANALYTICS_TRACK = "analytics.track"

        private val INJECTION_SCRIPT = """
            (() => {
                const channel = window.$KEEPER_BRIDGE_CHANNEL;
                if (window !== window.top || !channel) {
                    return;
                }
                channel.addEventListener('message', (event) => {
                    window.dispatchEvent(new MessageEvent('message', { data: JSON.parse(event.data) }));
                });
            })();
        """.trimIndent()
    }
}
