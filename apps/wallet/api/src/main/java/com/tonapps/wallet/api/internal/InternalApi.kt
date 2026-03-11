package com.tonapps.wallet.api.internal

import android.content.Context
import androidx.collection.ArrayMap
import androidx.core.net.toUri
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.extensions.isDebug
import com.tonapps.extensions.locale
import com.tonapps.extensions.map
import com.tonapps.network.get
import com.tonapps.network.postJSON
import com.tonapps.wallet.api.entity.ConfigResponseEntity
import com.tonapps.wallet.api.entity.EthenaEntity
import com.tonapps.wallet.api.entity.NotificationEntity
import com.tonapps.wallet.api.entity.OnRampArgsEntity
import com.tonapps.wallet.api.entity.StoryEntity
import com.tonapps.wallet.api.readBody
import com.tonapps.wallet.api.withRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.Locale

internal class InternalApi(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val appVersionName: String
) {

    private var _deviceCountry: String? = null
    private var _storeCountry: String? = null
    private var _apiEndpoint = "https://api.tonkeeper.com".toUri()

    val country: String
        get() = _storeCountry ?: _deviceCountry ?: Locale.getDefault().country.uppercase()

    fun setCountry(deviceCountry: String, storeCountry: String?) {
        _deviceCountry = deviceCountry.uppercase()
        _storeCountry = storeCountry?.uppercase()
    }

    fun setApiUrl(url: String) {
        _apiEndpoint = url.toUri()
    }

    private fun endpoint(
        path: String,
        network: TonNetwork,
        platform: String,
        build: String,
        boot: Boolean = false,
        queryParams: Map<String, String> = emptyMap(),
        bootFallback: Boolean = false,
    ): String = runBlocking {
        val builder = if (bootFallback) {
            "https://block.tonkeeper.com".toUri().buildUpon()
        } else if (boot) {
            "https://boot.tonkeeper.com".toUri().buildUpon()
        } else {
            _apiEndpoint.buildUpon()
        }
        val chainName = when (network) {
            TonNetwork.TESTNET -> "testnet"
            TonNetwork.MAINNET -> "mainnet"
            TonNetwork.TETRA -> "mainnet"
        }
        builder
            .appendEncodedPath(path)
            .appendQueryParameter("lang", context.locale.language)
            .appendQueryParameter("build", build)
            .appendQueryParameter("platform", platform)
            .appendQueryParameter("chainName", chainName)
            .appendQueryParameter("bundle_id", context.packageName)

        _storeCountry?.let {
            builder.appendQueryParameter("store_country_code", it)
        }
        _deviceCountry?.let {
            builder.appendQueryParameter("device_country_code", it)
        }

        queryParams.forEach {
            builder.appendQueryParameter(it.key, it.value)
        }

        builder.build().toString()
    }

    private fun request(
        path: String,
        network: TonNetwork,
        platform: String = "android",
        build: String = appVersionName,
        locale: Locale,
        boot: Boolean = false,
        queryParams: Map<String, String> = emptyMap(),
        bootFallback: Boolean = false,
    ): JSONObject {
        val url = endpoint(path, network, platform, build, boot, queryParams, bootFallback)
        val headers = ArrayMap<String, String>()
        headers["Accept-Language"] = locale.toString()
        val body = withRetry {
            okHttpClient.get(url, headers)
        } ?: throw IllegalStateException("Internal API request failed")
        return JSONObject(body)
    }

    private fun swapEndpoint(prefix: String, path: String): String {
        val builder = prefix.toUri().buildUpon()
            .appendEncodedPath(path)
        _deviceCountry?.let {
            builder.appendQueryParameter("device_country_code", _deviceCountry)
            builder.appendQueryParameter("country", _storeCountry ?: _deviceCountry)
        }
        _storeCountry?.let {
            builder.appendQueryParameter("store_country_code", _storeCountry)
        }
        return builder.build().toString()
    }

    fun getOnRampData(prefix: String) = withRetry {
        okHttpClient.get(swapEndpoint(prefix, "v2/onramp/currencies"))
    }

    fun getOnRampPaymentMethods(prefix: String, currency: String) = withRetry {
        okHttpClient.get(swapEndpoint(prefix, "v2/onramp/payment_methods"))
    }

    fun getOnRampMerchants(prefix: String) = withRetry {
        okHttpClient.get(swapEndpoint(prefix, "v2/onramp/merchants"))
    }

    fun calculateOnRamp(prefix: String, args: OnRampArgsEntity): String? {
        val json = args.toJSON()
        _deviceCountry?.let { json.put("country", _deviceCountry) }
        return withRetry {
            okHttpClient.postJSON(
                swapEndpoint(prefix, "v2/onramp/calculate"),
                json.toString()
            ).readBody()
        }
    }

    fun getNotifications(): List<NotificationEntity> {
        val json = request(
            path = "notifications",
            network = TonNetwork.MAINNET,
            locale = context.locale,
            boot = false
        )
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
            !domain.startsWith("@") && !domain.startsWith("*.") && maskDomains.none { mask ->
                domain.endsWith(
                    ".$mask"
                )
            }
        }

        return (maskDomains + cleanDomains + telegramBots).toTypedArray()
    }

    fun getBrowserApps(network: TonNetwork, locale: Locale): JSONObject {
        val data = request("apps/popular", network, locale = locale)
        return data.getJSONObject("data")
    }

    fun getFiatMethods(network: TonNetwork = TonNetwork.MAINNET, locale: Locale): JSONObject {
        val data = request("fiat/methods", network, locale = locale)
        return data.getJSONObject("data")
    }

    fun downloadConfig(fallback: Boolean = false): ConfigResponseEntity? {
        return try {
            val json = request(
                "keys/all",
                network = TonNetwork.MAINNET,
                locale = context.locale,
                boot = true,
                bootFallback = fallback
            )
            ConfigResponseEntity(json, context.isDebug)
        } catch (e: Throwable) {
            if (!fallback) {
                downloadConfig(true)
            } else {
                FirebaseCrashlytics.getInstance().recordException(e)
                null
            }
        }
    }

    fun getStories(id: String): StoryEntity.Stories? {
        return try {
            val json = request(
                path = "stories/$id",
                network = TonNetwork.MAINNET,
                locale = context.locale,
                boot = false
            )
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
            val json = request(
                path = "my/ip",
                network = TonNetwork.MAINNET,
                locale = context.locale,
                boot = false
            )
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

    fun getEthena(accountId: String): EthenaEntity? = withRetry {
        val json = request(
            path = "staking/ethena",
            network = TonNetwork.MAINNET,
            locale = context.locale,
            boot = false,
            queryParams = mapOf("address" to accountId)
        )
        EthenaEntity(json)
    }

}