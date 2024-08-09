package com.tonapps.wallet.data.browser.source

import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.browser.entities.BrowserDataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal class RemoteDataSource(
    private val api: API
) {

    suspend fun load(testnet: Boolean, locale: Locale): BrowserDataEntity? = withContext(Dispatchers.IO) {
        try {
            BrowserDataEntity(api.getBrowserApps(testnet, locale))
        } catch (e: Throwable) {
            null
        }
    }
}