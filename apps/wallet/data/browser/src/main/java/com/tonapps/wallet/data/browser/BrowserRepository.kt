package com.tonapps.wallet.data.browser

import android.content.Context
import com.tonapps.wallet.api.API
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

    suspend fun load(country: String): BrowserDataEntity? = withContext(Dispatchers.IO) {
        loadLocal(country) ?: loadRemote(country)
    }

    private fun loadLocal(country: String): BrowserDataEntity? {
        return localDataSource.getCache(country)
    }

    private fun loadRemote(country: String): BrowserDataEntity? {
        val data = remoteDataSource.load() ?: return null
        localDataSource.setCache(country, data)
        return data
    }
}