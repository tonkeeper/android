package com.tonapps.wallet.data.browser.source

import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.browser.entities.BrowserDataEntity

internal class RemoteDataSource(
    private val api: API
) {

    fun load(): BrowserDataEntity? {
        return try {
            BrowserDataEntity(api.getBrowserApps())
        } catch (e: Throwable) {
            null
        }
    }
}