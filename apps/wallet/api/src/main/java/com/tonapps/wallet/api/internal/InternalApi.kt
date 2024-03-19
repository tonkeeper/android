package com.tonapps.wallet.api.internal

import android.content.Context
import com.tonapps.network.Network
import com.tonapps.wallet.api.entity.ConfigEntity
import org.json.JSONObject

internal class InternalApi(
    private val context: Context
) {

    private fun endpoint(path: String): String {
        return "https://api.tonkeeper.com/$path?lang=en&build=3.5.0&platform=android_x"
    }

    private fun request(path: String): JSONObject {
        val url = endpoint(path)
        return JSONObject(Network.get(url))
    }

    fun downloadConfig(): ConfigEntity? {
        return try {
            val json = request("keys")
            ConfigEntity(json)
        } catch (e: Throwable) {
            null
        }
    }

}