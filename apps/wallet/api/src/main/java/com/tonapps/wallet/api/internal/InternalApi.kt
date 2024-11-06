package com.tonapps.wallet.api.internal

import android.content.Context
import android.net.Uri
import android.util.ArrayMap
import android.util.Log
import com.tonapps.extensions.appVersionName
import com.tonapps.extensions.isDebug
import com.tonapps.extensions.locale
import com.tonapps.network.get
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.entity.NotificationEntity
import com.tonapps.wallet.api.withRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.Locale

internal class InternalApi(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
) {

    private fun endpoint(
        path: String,
        testnet: Boolean,
        platform: String,
        build: String
    ): String {
        val builder = Uri.Builder()
        builder.scheme("https")
            .authority("api.tonkeeper.com")
            .appendEncodedPath(path)
            .appendQueryParameter("lang", context.locale.language)
            .appendQueryParameter("build", build)
            .appendQueryParameter("platform", platform)
            .appendQueryParameter("chainName", if (testnet) "testnet" else "mainnet")
        return builder.build().toString()
    }

    private fun request(
        path: String,
        testnet: Boolean,
        platform: String = "android", // "android_x"
        build: String = context.appVersionName,
        locale: Locale,
    ): JSONObject {
        val url = endpoint(path, testnet, platform, build)
        val headers = ArrayMap<String, String>()
        headers["Accept-Language"] = locale.toString()
        val body = okHttpClient.get(url, headers)
        return JSONObject(body)
    }

    fun getNotifications(): List<NotificationEntity> {
        val json = request("notifications", false, locale = context.locale)
        val array = json.getJSONArray("notifications")
        val list = mutableListOf<NotificationEntity>()
        for (i in 0 until array.length()) {
            list.add(NotificationEntity(array.getJSONObject(i)))
        }
        return list.toList()
    }

    fun getBrowserApps(testnet: Boolean, locale: Locale): JSONObject {
        val data = request("apps/popular", testnet, locale = locale)
        return data.getJSONObject("data")
    }

    fun getFiatMethods(testnet: Boolean = false, locale: Locale): JSONObject {
        val data = request("fiat/methods", testnet, locale = locale)
        return data.getJSONObject("data")
    }

    fun downloadConfig(testnet: Boolean): ConfigEntity? {
        return try {
            val json = request("keys", testnet, locale = context.locale)
            ConfigEntity(json, context.isDebug)
        } catch (e: Throwable) {
            null
        }
    }

    suspend fun resolveCountry(): String? = withContext(Dispatchers.IO) {
        try {
            val country = withRetry {
                okHttpClient.get("https://boot.tonkeeper.com/my/ip")
            }?.let {
                JSONObject(it).getString("country")
            }
            if (country.isNullOrBlank()) {
                null
            } else {
                country.uppercase()
            }
        } catch (e: Throwable) {
            null
        }
    }

}