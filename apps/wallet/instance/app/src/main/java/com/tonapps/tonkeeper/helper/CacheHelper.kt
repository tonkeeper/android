package com.tonapps.tonkeeper.helper

import com.tonapps.tonkeeper.core.history.list.item.HistoryItem
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.ScreenCacheSource

class CacheHelper(
    private val screenCacheSource: ScreenCacheSource
) {

    private companion object {
        private const val EVENTS_CACHE_NAME = "events"
    }

    fun getEventsCached(wallet: WalletEntity): List<HistoryItem> {
        return screenCacheSource.get(EVENTS_CACHE_NAME, wallet.id) {
            HistoryItem.createFromParcel(it)
        }
    }

    fun setEventsCached(wallet: WalletEntity, uiItems: List<HistoryItem>) {
        if (uiItems.isNotEmpty()) {
            screenCacheSource.set(EVENTS_CACHE_NAME, wallet.id, uiItems)
        }
    }
}