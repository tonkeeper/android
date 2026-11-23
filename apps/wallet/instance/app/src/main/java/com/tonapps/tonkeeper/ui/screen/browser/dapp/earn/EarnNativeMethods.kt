package com.tonapps.tonkeeper.ui.screen.browser.dapp.earn

import com.tonapps.webview.bridge.InAppBridgeContract.Effect
import com.tonapps.tonkeeper.ui.screen.browser.dapp.DAppPageEvent
import com.tonapps.tonkeeper.ui.screen.browser.dapp.nativebridge.NativeBridgeProtocol
import com.tonapps.tonkeeper.ui.screen.browser.dapp.nativebridge.NativeEvent
import com.tonapps.tonkeeper.ui.screen.browser.dapp.nativebridge.NativeMethodResult
import com.tonapps.tonkeeper.ui.screen.browser.dapp.nativebridge.NativeMethods
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONObject

internal class EarnNativeMethods(
    private val onTrack: (event: String, params: MutableMap<String, Any>) -> Unit,
    private val currentTheme: () -> String,
) : NativeMethods() {

    private val themeEvents = MutableSharedFlow<NativeEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    // The config, theme included, is injected once per attach and serves every document after it.
    @Volatile
    private var injectedTheme: String? = null

    // What the current document shows; null until it is ready.
    private var documentTheme: String? = null

    override val events: Flow<NativeEvent> = themeEvents

    /** For the injected config: every document of this attach starts with this theme. */
    fun injectTheme(): String {
        return currentTheme().also { injectedTheme = it }
    }

    fun onThemeChanged() {
        themeEvent()?.let(themeEvents::tryEmit)
    }

    override fun onDocumentReady(): List<NativeEvent> {
        documentTheme = injectedTheme
        return listOfNotNull(themeEvent())
    }

    override fun invoke(method: String, params: JSONObject): NativeMethodResult? {
        return when (method) {
            NAVIGATE_BACK, CLOSE -> close()
            TOAST -> toast(params)
            ANALYTICS_TRACK -> track(params)
            else -> null
        }
    }

    private fun themeEvent(): NativeEvent? {
        val shown = documentTheme ?: return null
        val theme = currentTheme()
        if (theme == shown) {
            return null
        }
        documentTheme = theme
        return NativeEvent(NativeBridgeProtocol.EVENT_THEME_CHANGED, JSONObject().put("theme", theme))
    }

    private fun close(): NativeMethodResult {
        return NativeMethodResult.Success(effect = Effect.Close)
    }

    private fun toast(params: JSONObject): NativeMethodResult {
        val title = (params.opt("title") as? String)?.takeIf { it.isNotBlank() }
            ?: return NativeMethodResult.Failure(ERROR_INVALID_PARAMS, "Missing title")
        return NativeMethodResult.Success(effect = Effect.Toast(title))
    }

    private fun track(params: JSONObject): NativeMethodResult {
        val event = params.optString("event").takeIf { it.isNotBlank() }
            ?: return NativeMethodResult.Failure(ERROR_INVALID_PARAMS, "Missing event")
        onTrack(event, DAppPageEvent.params(DAppPageEvent.rawParams(params.optJSONObject("params")), EVENT_PROVIDER))
        return NativeMethodResult.Success()
    }

    private companion object {
        private const val NAVIGATE_BACK = "ui.navigateBack"
        private const val CLOSE = "ui.close"
        private const val TOAST = "ui.toast"
        private const val ANALYTICS_TRACK = "analytics.track"
        private const val EVENT_PROVIDER = "vaults.fyi"
    }
}