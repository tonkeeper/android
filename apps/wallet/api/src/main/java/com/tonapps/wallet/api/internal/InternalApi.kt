package com.tonapps.wallet.api.internal

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tonapps.extensions.isDebug
import com.tonapps.extensions.locale
import com.tonapps.extensions.packageInfo
import com.tonapps.network.get
import com.tonapps.wallet.api.entity.ConfigEntity
import okhttp3.OkHttpClient
import org.json.JSONObject

internal class InternalApi(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
) {

    private fun endpoint(path: String): String {
        val builder = Uri.Builder()
        builder.scheme("https")
            .authority("api.tonkeeper.com")
            .appendEncodedPath(path)
            .appendQueryParameter("lang", context.locale.language)
            .appendQueryParameter("build", context.packageInfo.versionName.removeSuffix("-debug"))
            .appendQueryParameter("platform", "android_x")
        return builder.build().toString()
    }

    private fun request(path: String): JSONObject {
        val url = endpoint(path)
        val body = okHttpClient.get(url)
        return JSONObject(body)
    }

    fun getBrowserApps(): JSONObject {
        val data = request("apps/popular")
        return data.getJSONObject("data")
    }

    fun downloadConfig(): ConfigEntity? {
        return try {
            val json = request("keys")
            ConfigEntity(json, context.isDebug)
        } catch (e: Throwable) {
            null
        }
    }

}