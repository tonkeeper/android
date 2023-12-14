package com.tonkeeper.api.internal

import org.json.JSONObject
import core.network.Network

object Tonkeeper {

    private fun endpoint(path: String): String {
        return "https://api.tonkeeper.com/$path?lang=en&build=3.5.0&chainName=mainnet&platform=android"
    }

    fun get(path: String): JSONObject {
        val url = endpoint(path)
        val body = Network.get(url)
        return JSONObject(body)
    }
}