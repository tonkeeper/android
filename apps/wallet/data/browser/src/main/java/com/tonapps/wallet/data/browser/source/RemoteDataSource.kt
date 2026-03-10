package com.tonapps.wallet.data.browser.source

import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.log.L
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.browser.entities.BrowserDataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal class RemoteDataSource(
    private val api: API
) {

    suspend fun load(network: TonNetwork, locale: Locale): BrowserDataEntity? = withContext(Dispatchers.IO) {
        try {
            BrowserDataEntity(api.getBrowserApps(network, locale))
        } catch (e: Throwable) {
            FirebaseCrashlytics.getInstance().recordException(e)
            null
        }
    }
}