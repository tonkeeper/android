package com.tonapps.portfolio.screens.raffle

import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.StoryEntity
import com.tonapps.wallet.data.settings.SettingsRepository

class ConfigStoriesAutoShow(
    private val api: API,
    private val settingsRepository: SettingsRepository,
    private val session: StoryAutoShowSession,
) {

    private val storiesWithoutAutoShow = mutableSetOf<Pair<String?, List<String>>>()

    suspend fun showOnce(wallet: WalletEntity, show: suspend (StoryEntity.Stories) -> Boolean) {
        if (session.hasShown(wallet.id)) {
            return
        }
        val walletId = wallet.id.takeIf { wallet.type == WalletType.Multichain }
        val configStories = api.getConfig(wallet.network).stories
        if ((walletId to configStories) in storiesWithoutAutoShow) {
            return
        }
        val candidates = configStories.filter { !settingsRepository.isStoriesViewed(it) }
        if (candidates.isEmpty()) {
            return
        }
        val loaded = api.getStories(
            ids = candidates,
            walletId = walletId,
            network = wallet.network,
            isNew = !settingsRepository.hadWalletsOnMultichainRelease,
        )
        val stories = candidates.firstNotNullOfOrNull { id -> loaded.find { it.id == id && it.isAutoShow } }
        if (stories == null) {
            if (loaded.isNotEmpty()) {
                storiesWithoutAutoShow.add(walletId to configStories)
            }
            return
        }
        if (!session.tryAcquire(wallet.id, StoryAutoShowSession.Owner.Config)) {
            return
        }
        var shown = false
        try {
            shown = show(stories)
        } finally {
            if (!shown) {
                session.release(wallet.id, StoryAutoShowSession.Owner.Config)
            }
        }
    }
}
