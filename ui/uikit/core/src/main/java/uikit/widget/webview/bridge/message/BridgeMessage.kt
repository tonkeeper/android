package uikit.widget.webview.bridge.message

import org.json.JSONObject

abstract class BridgeMessage(
    val type: Type
) {

    enum class Type(val value: String) {
        InvokeRnFunc("invokeRnFunc"),
        FunctionResponse("functionResponse"),
        Event("event"),
    }

    abstract fun createJSON(): JSONObject
}