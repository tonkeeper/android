package com.tonapps.tonkeeper.ui.screen.swap

import android.util.Log
import com.tonapps.wallet.data.core.entity.SignRequestEntity
import org.json.JSONArray
import uikit.widget.webview.bridge.JsBridge

class StonfiBridge2(
    val address: String,
    val close: () -> Unit,
    val sendTransaction: suspend (request: SignRequestEntity) -> String?
): JsBridge("tonkeeperStonfi") {

    override val availableFunctions = arrayOf("close", "sendTransaction")

    init {
        keys["address"] = address
    }

    override suspend fun invokeFunction(name: String, args: JSONArray): Any? {
        if (name == "close") {
            close()
            return null
        } else if (name == "sendTransaction" && args.length() == 1) {
            val request = SignRequestEntity(args.getJSONObject(0))
            return sendTransaction(request)
        }
        throw IllegalArgumentException("Unknown function: $name")
    }

}