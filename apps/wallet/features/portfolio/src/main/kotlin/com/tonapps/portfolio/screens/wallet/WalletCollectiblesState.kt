package com.tonapps.portfolio.screens.wallet

import com.tonapps.wallet.data.collectibles.entities.NftEntity

data class WalletCollectiblesState(
    val items: List<NftEntity>,
    val allHidden: Boolean = false,
) {
    val previewItems: List<NftEntity>
        get() = items.take(PREVIEW_LIMIT)

    val showSeeAll: Boolean
        get() = items.size > PREVIEW_LIMIT

    val isVisible: Boolean
        get() = items.isNotEmpty() || allHidden

    private companion object {
        const val PREVIEW_LIMIT = 10
    }
}
