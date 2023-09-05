package com.tonkeeper.api.method

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.tonkeeper.api.Network
import org.json.JSONObject

abstract class BaseMethod<R>(path: String) {

    private val uriBuilder = Uri.parse(Network.ENDPOINT + path).buildUpon()
    private val url: String by lazy { uriBuilder.toString() }

    fun querySet(key: String, value: Any) {
        uriBuilder.appendQueryParameter(key, value.toString())
    }

    @Throws(Exception::class)
    fun execute(attempt: Int = 0): R {
        if (attempt >= 5) {
            throw Exception("Too many attempts")
        }
        val builder = Network.newRequest(url)
        val request = builder.build()
        val response = Network.newCall(request).execute()
        val body = response.body?.string()!!
        val code = response.code
        return if (response.isSuccessful) {
            val json = JSONObject(body)
            parseJSON(json)
        } else if (code == 429) {
            SystemClock.sleep(3000)
            execute(attempt + 1)
        } else {
            throw Exception("Response is not successful: ${response.code}: $body")
        }
    }

    abstract fun parseJSON(response: JSONObject): R

}