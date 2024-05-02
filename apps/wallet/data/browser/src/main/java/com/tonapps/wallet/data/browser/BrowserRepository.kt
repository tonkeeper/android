package com.tonapps.wallet.data.browser

import android.content.Context
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.browser.entities.BrowserAppEntity
import com.tonapps.wallet.data.browser.entities.BrowserDataEntity
import com.tonapps.wallet.data.browser.source.LocalDataSource
import com.tonapps.wallet.data.browser.source.RemoteDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BrowserRepository(
    private val context: Context,
    private val api: API
) {

    private val localDataSource = LocalDataSource(context)
    private val remoteDataSource = RemoteDataSource(api)

    suspend fun search(
        country: String,
        query: String
    ): List<BrowserAppEntity> {
        val data = load(country) ?: return emptyList()
        val all = data.categories.map { it.apps }.flatten()
        return all.filter {
            it.name.contains(query, ignoreCase = true) ||
            it.description.contains(query, ignoreCase = true) ||
            it.url.toString().contains(query, ignoreCase = true)
        }.distinctBy { it.url }
    }

    suspend fun load(country: String): BrowserDataEntity? = withContext(Dispatchers.IO) {
        loadLocal(country) ?: loadRemote(country)
    }

    private fun loadLocal(country: String): BrowserDataEntity? {
        return localDataSource.getCache(country)
    }

    fun loadRemote(country: String): BrowserDataEntity? {
        val data = remoteDataSource.load() ?: return null
        localDataSource.setCache(country, data)
        return data
    }
}