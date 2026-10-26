package com.tonapps.tonkeeper.ui.screen.browser.dapp.nativebridge

import com.tonapps.tonkeeper.ui.screen.browser.dapp.inapp.InAppBridgeContract.Effect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.json.JSONObject

internal sealed interface NativeMethodResult {

    /** [effect] reaches the screen after the page has its answer. */
    class Success(
        val value: JSONObject = JSONObject(),
        val effect: Effect? = null,
    ) : NativeMethodResult

    class Failure(val code: Int, val message: String) : NativeMethodResult
}

/** What the page hears through `window.native.on(name, handler)`. */
internal class NativeEvent(val name: String, val payload: JSONObject)

/** What a dApp may call through `window.native`. */
internal abstract class NativeMethods {

    /** Raised outside any call; reaches only a document that has said it is ready. */
    open val events: Flow<NativeEvent> = emptyFlow()

    fun handle(method: String, params: JSONObject): NativeMethodResult {
        return invoke(method, params)
            ?: NativeMethodResult.Failure(ERROR_UNSUPPORTED_METHOD, "Unsupported method")
    }

    /** What a new document has to catch up on: it starts from the config injected at attach. */
    open fun onDocumentReady(): List<NativeEvent> = emptyList()

    /** null: the dApp called a method this screen does not have. */
    protected abstract fun invoke(method: String, params: JSONObject): NativeMethodResult?

    companion object {
        const val ERROR_UNSUPPORTED_METHOD = 1
        const val ERROR_INVALID_PARAMS = 2
    }
}