package com.tonapps.wallet.data.browser

import android.content.Context
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.browser.entities.BrowserAppEntity
import com.tonapps.wallet.data.browser.entities.BrowserDataEntity
import com.tonapps.wallet.data.browser.source.LocalDataSource
import com.tonapps.wallet.data.browser.source.RemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.util.Locale

class BrowserRepository(context: Context, api: API) {

    private val localDataSource: LocalDataSource by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        LocalDataSource(context)
    }

    private val remoteDataSource = RemoteDataSource(api)

    suspend fun search(
        country: String,
        query: String,
        testnet: Boolean,
        locale: Locale
    ): List<BrowserAppEntity> {
        val data = load(country, testnet, locale) ?: return emptyList()
        val all = data.categories.map { it.apps }.flatten()
        return all.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true) ||
            it.url.toString().contains(query, ignoreCase = true)
        }.distinctBy { it.url }
    }

    suspend fun getApps(country: String, testnet: Boolean, locale: Locale): List<BrowserAppEntity> {
        return load(country, testnet, locale)?.categories?.map { it.apps }?.flatten() ?: emptyList()
    }

    fun dataFlow(
        country: String,
        testnet: Boolean,
        locale: Locale
    ) = flow {
        loadLocal(country, locale)?.let { emit(it) }
        load(country, testnet, locale)?.let { emit(it) }
    }

    suspend fun load(country: String, testnet: Boolean, locale: Locale): BrowserDataEntity? = withContext(Dispatchers.IO) {
        loadLocal(country, locale) ?: loadRemote(country, testnet, locale)
    }

    private fun loadLocal(country: String, locale: Locale): BrowserDataEntity? {
        val key = cacheKey(country, locale)
        return localDataSource.getCache(key)
    }

    private fun cacheKey(country: String, locale: Locale) = "browser_data_${country}_${locale.language}"

    suspend fun loadRemote(country: String, testnet: Boolean, locale: Locale): BrowserDataEntity? {
        val data = remoteDataSource.load(testnet, locale) ?: return null
        val key = cacheKey(country, locale)
        localDataSource.setCache(key, data)
        return data
    }
}