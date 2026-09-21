package com.tonapps.wallet.data.browser.source

import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.bus.core.IssueHelper
import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.browser.entities.BrowserDataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal class RemoteDataSource(
    private val api: API
) {

    suspend fun load(network: TonNetwork, locale: Locale, walletId: String?): BrowserDataEntity? = withContext(Dispatchers.IO) {
        try {
            BrowserDataEntity(api.getBrowserApps(network, locale, walletId))
        } catch (e: Throwable) {
            IssueHelper.recordException(e)
            null
        }
    }
}