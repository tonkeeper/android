package com.tonapps.wallet.data.browser

import android.content.Context
import android.net.Uri
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.browser.entities.BrowserAppEntity
import com.tonapps.wallet.data.browser.entities.BrowserDataEntity
import com.tonapps.wallet.data.browser.source.LocalDataSource
import com.tonapps.wallet.data.browser.source.RemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class BrowserRepository(context: Context, private val api: API) {

    private val localDataSource: LocalDataSource by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LocalDataSource(context)
    }

    private val remoteDataSource = RemoteDataSource(api)
    private val appCacheByHost = ConcurrentHashMap<String, BrowserAppEntity>(100, 1.0f, 2)

    suspend fun search(
        country: String,
        query: String,
        network: TonNetwork = TonNetwork.MAINNET,
        locale: Locale,
        walletId: String?,
    ): List<BrowserAppEntity> {
        val data = load(country, network, locale, walletId) ?: return emptyList()
        val all = data.categories.map { it.apps }.flatten()
        return all.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true) ||
            it.url.toString().contains(query, ignoreCase = true)
        }.distinctBy { it.url }
    }

    suspend fun isTrustedApp(
        country: String,
        network: TonNetwork,
        locale: Locale,
        deeplink: Uri,
        walletId: String?,
    ): Boolean {
        if (deeplink.host == "dapp.aeon.xyz" || deeplink.host == "tonkeeper.com" || deeplink.host?.endsWith(".tonkeeper.com") == true) {
            return true
        }
        val host = deeplink.host ?: return false
        val apps = getApps(country, network, locale, walletId)
        for (app in apps) {
            if (app.useTG) {
                continue
            } else if (host == app.host) {
                return true
            }
        }
        return false
    }

    suspend fun getApps(
        country: String,
        network: TonNetwork,
        locale: Locale,
        walletId: String?,
    ): List<BrowserAppEntity> {
        return load(country, network, locale, walletId)?.categories?.map { it.apps }?.flatten()
            ?: emptyList()
    }

    suspend fun getApp(
        country: String,
        network: TonNetwork,
        locale: Locale,
        uri: Uri,
        walletId: String?,
    ): BrowserAppEntity? {
        val host = uri.host ?: return null
        val hostKey = walletId?.let { "${host}_$it" } ?: host
        val browserApp = appCacheByHost[hostKey]
        if (browserApp != null) {
            return browserApp
        }
        val apps = getApps(country, network, locale, walletId)
        for (app in apps) {
            if (app.useTG) {
                continue
            } else if (app.host == host) {
                appCacheByHost[hostKey] = app
                return app
            }
        }
        return null
    }

    fun dataFlow(
        country: String,
        network: TonNetwork,
        locale: Locale,
        walletId: String?,
    ) = flow {
        loadLocal(country, locale, walletId)?.let { emit(it) }
        loadRemote(country, network, locale, walletId)?.let { emit(it) }
    }

    suspend fun load(
        country: String,
        network: TonNetwork,
        locale: Locale,
        walletId: String?,
    ): BrowserDataEntity? = withContext(Dispatchers.IO) {
        loadLocal(country, locale, walletId) ?: loadRemote(country, network, locale, walletId)
    }

    suspend fun loadCategories(
        country: String,
        network: TonNetwork,
        locale: Locale,
        walletId: String?,
    ): List<String> {
        return load(country, network, locale, walletId)?.categories?.map { it.id } ?: emptyList()
    }

    private fun loadLocal(
        country: String,
        locale: Locale,
        walletId: String?,
    ): BrowserDataEntity? {
        val key = cacheKey(country, locale, walletId)
        return localDataSource.getCache(key)
    }

    private fun cacheKey(country: String, locale: Locale, walletId: String?): String {
        val base = "browser_data_v2_${country}_${locale.language}"
        return walletId?.let { "${base}_$it" } ?: base
    }

    suspend fun loadRemote(
        country: String,
        network: TonNetwork,
        locale: Locale,
        walletId: String?,
    ): BrowserDataEntity? {
        val data = remoteDataSource.load(network, locale, walletId) ?: return null
        val key = cacheKey(country, locale, walletId)
        localDataSource.setCache(key, data)
        return data
    }
}
