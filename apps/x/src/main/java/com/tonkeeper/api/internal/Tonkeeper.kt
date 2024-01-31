package com.tonkeeper.api.internal

import network.Network
import org.json.JSONObject

object Tonkeeper {

    const val HOST = "api.tonkeeper.com"

    private fun endpoint(path: String): String {
        return "https://${HOST}/$path?lang=en&build=3.5.0&chainName=mainnet&platform=android"
    }

    fun get(path: String): JSONObject {
        val url = endpoint(path)
        val body = Network.get(url)
        return JSONObject(body)
    }
}