package com.tonapps.wallet.api.internal

import android.content.Context
import androidx.core.net.toUri
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.bus.core.IssueHelper
import com.tonapps.extensions.isDebug
import com.tonapps.extensions.locale
import com.tonapps.extensions.map
import com.tonapps.network.get
import com.tonapps.network.isVpnActive
import com.tonapps.wallet.api.Constants
import com.tonapps.wallet.api.configs.CountryConfig
import com.tonapps.wallet.api.entity.BannerEntity
import com.tonapps.wallet.api.entity.ConfigResponseEntity
import com.tonapps.wallet.api.entity.EthenaEntity
import com.tonapps.wallet.api.entity.NotificationEntity
import com.tonapps.wallet.api.entity.StoryEntity
import com.tonapps.wallet.api.withRetry
import io.Serializer
import io.tonendpointapi.apis.ContentApi
import io.tonendpointapi.apis.EthenaApi
import io.tonendpointapi.apis.FiatApi
import io.tonendpointapi.apis.KeysApi
import io.tonendpointapi.apis.NotificationsApi
import io.tonendpointapi.apis.StoriesApi
import io.tonendpointapi.apis.SystemApi
import io.tonendpointapi.models.Currency
import io.tonendpointapi.models.GetFiatMethods200ResponseData
import io.tonendpointapi.models.GetPopularApps200ResponseData
import io.tonendpointapi.models.StoryInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import okhttp3.Call
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private const val MULTICHAIN_FEATURE = "multichain"

internal class InternalApi(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val appVersionName: String
) {

    @Volatile
    private var config: CountryConfig? = null

    val hasCountryConfig: Boolean
        get() = config != null

    private val configFailureRecorded = AtomicBoolean(false)

    private var _apiEndpoint = Constants.TONKEEPER_API.toUri()

    val country: String
        get() = config?.storeCountry ?: config?.deviceCountry?: config?.simCountry ?: Locale.getDefault().country.uppercase()

    fun setConfig(newConfig: CountryConfig) {
        config = newConfig
    }

    fun setApiUrl(url: String) {
        _apiEndpoint = url.toUri()
    }

    // The context the app sends to every endpoint, whether its operation declares the parameter or not.
    // It replaces what the generated client fills in: its `lang` defaults to "en".
    private fun client(network: TonNetwork, locale: Locale, walletId: String?): Call.Factory {
        val query = buildMap {
            put("lang", context.locale.language)
            put("build", appVersionName)
            put("platform", "android")
            put("chainName", when (network) {
                TonNetwork.TESTNET -> "testnet"
                TonNetwork.MAINNET -> "mainnet"
            })
            put("bundle_id", context.packageName)
            config?.storeCountry?.let { put("store_country_code", it) }
            config?.deviceCountry?.let { put("device_country_code", it) }
            config?.simCountry?.let { put("sim_country", it) }
            config?.timezone?.let { put("timezone", it) }
            put("is_vpn_active", context.isVpnActive().toString())
            walletId?.let { put("wallet_id", it) }
            put("features", MULTICHAIN_FEATURE)
        }
        return okHttpClient.withQuery(query, locale.toString())
    }

    private fun <A, T> request(
        api: (String, Call.Factory) -> A,
        network: TonNetwork = TonNetwork.MAINNET,
        locale: Locale = context.locale,
        walletId: String? = null,
        host: String = _apiEndpoint.toString(),
        call: A.() -> T,
    ): T = withRetry { api(host, client(network, locale, walletId)).call() }
        ?: throw IllegalStateException("Internal API request failed")

    private fun swapEndpoint(prefix: String, path: String): String {
        val builder = prefix.toUri().buildUpon()
            .appendEncodedPath(path)
        config?.deviceCountry?.let {
            builder.appendQueryParameter("device_country_code", it)
        }
        config?.storeCountry?.let {
            builder.appendQueryParameter("store_country_code", it)
        }
        config?.simCountry?.let {
            builder.appendQueryParameter("sim_country", it)
        }
        config?.timezone?.let {
            builder.appendQueryParameter("timezone", it)
        }
        builder.appendQueryParameter("is_vpn_active", context.isVpnActive().toString())
        return builder.build().toString()
    }

    fun getOnRampData(prefix: String): String? {
        return withRetry {
            okHttpClient.get(swapEndpoint(prefix, "v2/onramp/currencies"))
        }
    }

    fun getOnRampPaymentMethods(prefix: String, currency: String) = withRetry {
        okHttpClient.get(swapEndpoint(prefix, "v2/onramp/payment_methods"))
    }

    fun getOnRampMerchants(prefix: String) = withRetry {
        okHttpClient.get(swapEndpoint(prefix, "v2/onramp/merchants"))
    }

    fun getNotifications(walletId: String?): List<NotificationEntity> {
        return request(::NotificationsApi, walletId = walletId) { getNotifications() }
            .notifications
            .map(::NotificationEntity)
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

    fun getBrowserApps(network: TonNetwork, locale: Locale, walletId: String?): GetPopularApps200ResponseData {
        // The app never sent `with_ads`; the generated client would send its default, false.
        return request(::ContentApi, network, locale, walletId) { getPopularApps(withAds = null) }.data
    }

    fun getBanners(
        network: TonNetwork = TonNetwork.MAINNET,
        walletId: String?,
        isNew: Boolean,
    ): List<BannerEntity> = withRetry {
        request(::ContentApi, network, walletId = walletId) { getBanners(isNew = isNew) }
            .banners
            .map(::BannerEntity)
    } ?: emptyList()

    fun getCurrencies(network: TonNetwork = TonNetwork.MAINNET, locale: Locale, walletId: String?): List<Currency> {
        return request(::ContentApi, network, locale, walletId) { getCurrencies() }.currencies
    }

    fun getFiatMethods(network: TonNetwork = TonNetwork.MAINNET, locale: Locale, walletId: String?): GetFiatMethods200ResponseData? {
        return request(::FiatApi, network, locale, walletId) { getFiatMethods() }.data
    }

    fun downloadConfig(fallback: Boolean = false): ConfigResponseEntity? {
        return try {
            val host = if (fallback) {
                "https://block.tonkeeper.com"
            } else {
                "https://boot.tonkeeper.com"
            }
            val json = request(::KeysApi, host = host) { getAllKeys() }
            val config = ConfigResponseEntity(JSONObject(json.toString()), context.isDebug)
            configFailureRecorded.set(false)
            config
        } catch (e: Throwable) {
            if (!fallback) {
                downloadConfig(true)
            } else {
                if (configFailureRecorded.compareAndSet(false, true)) {
                    IssueHelper.recordException(e)
                }
                null
            }
        }
    }

    fun getStories(id: String, walletId: String?, isNew: Boolean): StoryEntity.Stories? {
        return try {
            val story = request(::StoriesApi, walletId = walletId) { getStories(storyId = id, isNew = isNew) }
            parseStories(story)
        } catch (e: Throwable) {
            IssueHelper.recordException(e)
            null
        }
    }

    fun getStories(
        ids: List<String>,
        walletId: String?,
        network: TonNetwork,
        isNew: Boolean,
    ): List<StoryEntity.Stories> {
        if (ids.isEmpty()) {
            return emptyList()
        }
        return try {
            request(::StoriesApi, network, walletId = walletId) {
                getStoriesBatch(ids = ids.joinToString(","), isNew = isNew)
            }.getValue("stories").jsonArray.mapNotNull { item ->
                try {
                    parseStories(Serializer.JSON.decodeFromJsonElement<StoryInfo>(item))
                } catch (e: Throwable) {
                    IssueHelper.recordException(e)
                    null
                }
            }
        } catch (e: Throwable) {
            IssueHelper.recordException(e)
            emptyList()
        }
    }

    private fun parseStories(story: StoryInfo): StoryEntity.Stories? {
        val list = story.pages.map(::StoryEntity)
        return if (list.isEmpty()) {
            null
        } else {
            StoryEntity.Stories(story.storyId, list, story.isAutoShow ?: false)
        }
    }

    suspend fun resolveCountry(): String? = withContext(Dispatchers.IO) {
        try {
            val country = request(::SystemApi) { myIp() }.country
            if (country.isBlank()) {
                null
            } else {
                country.uppercase()
            }
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            null
        }
    }

    fun getEthena(accountId: String, walletId: String?): EthenaEntity? = withRetry {
        EthenaEntity(request(::EthenaApi, walletId = walletId) { getStakingEthenaMethods(address = accountId) })
    }

}

internal fun Call.Factory.withQuery(query: Map<String, String>, acceptLanguage: String) = Call.Factory { request ->
    val url = request.url.newBuilder().apply {
        query.forEach { (name, value) -> setQueryParameter(name, value) }
    }.build()
    newCall(request.newBuilder().url(url).header("Accept-Language", acceptLanguage).build())
}
