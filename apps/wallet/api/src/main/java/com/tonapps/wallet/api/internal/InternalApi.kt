package com.tonapps.wallet.api.internal

import android.content.Context
import android.net.Uri
import android.util.ArrayMap
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.extensions.deviceCountry
import com.tonapps.extensions.getStoreCountry
import com.tonapps.extensions.isDebug
import com.tonapps.extensions.locale
import com.tonapps.extensions.map
import com.tonapps.network.get
import com.tonapps.network.postJSON
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.api.entity.NotificationEntity
import com.tonapps.wallet.api.entity.OnRampArgsEntity
import com.tonapps.wallet.api.entity.StoryEntity
import com.tonapps.wallet.api.withRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.math.BigDecimal
import java.util.Locale

internal class InternalApi(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val appVersionName: String
) {

    private fun endpoint(
        path: String,
        testnet: Boolean,
        platform: String,
        build: String,
        boot: Boolean = false,
    ): String = runBlocking {
        val builder = Uri.Builder()
        builder.scheme("https")
            .authority(if (boot) "boot.tonkeeper.com" else "api.tonkeeper.com")
            .appendEncodedPath(path)
            .appendQueryParameter("lang", context.locale.language)
            .appendQueryParameter("build", build)
            .appendQueryParameter("platform", platform)
            .appendQueryParameter("chainName", if (testnet) "testnet" else "mainnet")
            .appendQueryParameter("bundle_id", context.packageName)

        val storeCountry = context.getStoreCountry()
        storeCountry?.let {
            builder.appendQueryParameter("store_country_code", storeCountry)
        }
        builder.appendQueryParameter("device_country_code", context.deviceCountry)

        builder.build().toString()
    }

    private fun request(
        path: String,
        testnet: Boolean,
        platform: String = "android",
        build: String = appVersionName,
        locale: Locale,
        boot: Boolean = false,
    ): JSONObject {
        val url = endpoint(path, testnet, platform, build, boot)
        val headers = ArrayMap<String, String>()
        headers["Accept-Language"] = locale.toString()
        val body = withRetry {
            okHttpClient.get(url, headers)
        } ?: throw IllegalStateException("Internal API request failed")
        return JSONObject(body)
    }

    fun getOnRampData(country: String) = withRetry {
        okHttpClient.get("https://swap.tonkeeper.com/v2/onramp/currencies?country=${country.uppercase()}")
    }

    fun getEthenaStakingAPY(address: String): BigDecimal = withRetry {
        val json = request("ethena/staking?address=$address", false, locale = context.locale)
        BigDecimal.valueOf(json.getDouble("value"))
    } ?: BigDecimal.ZERO

    fun calculateOnRamp(args: OnRampArgsEntity) = withRetry {
        val url = "https://swap.tonkeeper.com/v2/onramp/calculate"
        okHttpClient.postJSON(url, args.toJSON().toString()).body?.string()
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

    fun getScamDomains(): Array<String> {
        val array = withRetry {
            okHttpClient.get("https://scam.tonkeeper.com/v1/scam/domains")
        }?.let { JSONObject(it).getJSONArray("items") } ?: return emptyArray()

        val domains = array.map { it.getString("url") }.map {
            if (it.startsWith("www.")) {
                "*.${it.substring(4)}"
            } else {
                it
            }
        }
        val telegramBots = domains.filter { it.startsWith("@") }.map { "t.me/${it.substring(1)}" }
        val maskDomains = domains.filter { it.startsWith("*.") }
        val cleanDomains = domains.filter { domain ->
            !domain.startsWith("@") && !domain.startsWith("*.") && maskDomains.none { mask -> domain.endsWith(".$mask") }
        }

        return (maskDomains + cleanDomains + telegramBots).toTypedArray()
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
            val json = request("keys", testnet, locale = context.locale, boot = true)
            ConfigEntity(json, context.isDebug)
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            null
        }
    }

    fun getStories(id: String): StoryEntity.Stories? {
        return try {
            val json = request("stories/$id", false, locale = context.locale)
            val pages = json.getJSONArray("pages")
            val list = mutableListOf<StoryEntity>()
            for (i in 0 until pages.length()) {
                list.add(StoryEntity(pages.getJSONObject(i)))
            }
            if (list.isEmpty()) {
                null
            } else {
                StoryEntity.Stories(id, list.toList())
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            null
        }
    }

    suspend fun resolveCountry(): String? = withContext(Dispatchers.IO) {
        try {
            val json = request("my/ip", false, locale = context.locale)
            val country = json.getString("country")
            if (country.isNullOrBlank()) {
                null
            } else {
                country.uppercase()
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            null
        }
    }

}