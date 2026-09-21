package com.tonapps.portfolio.screens.raffle

import com.tonapps.wallet.data.raffle.RaffleClock
import com.tonapps.wallet.data.raffle.RaffleRepository
import com.tonapps.wallet.data.raffle.debug.RaffleDebugStore
import com.tonapps.wallet.data.raffle.entities.RaffleEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.delay
import java.time.Duration

/**
 * Promo story auto-show: once per story, for multichain wallets while the
 * raffle is still running. Stories ship embedded in the raffle payload and
 * the backend can override them per phase, so a phase's new story (a new
 * story id) auto-shows again.
 */
class RaffleStoryAutoShow(
    private val raffleRepository: RaffleRepository,
    private val settingsRepository: SettingsRepository,
    private val debugStore: RaffleDebugStore,
    private val session: StoryAutoShowSession,
) {

    private val shownStoryIds = mutableSetOf<String>()
    private var seenDedupeResetCount = 0

    suspend fun prefetch(walletId: String) {
        raffleRepository.prefetch(walletId)
    }

    suspend fun showOnce(walletId: String, show: suspend (raffleId: String) -> Boolean) {
        val resetCount = debugStore.storyDedupeResetCount.value
        if (resetCount != seenDedupeResetCount) {
            seenDedupeResetCount = resetCount
            shownStoryIds.clear()
            session.reset()
        }
        val raffle = awaitStartedRaffle(walletId) ?: return
        val running = raffle.status == RaffleEntity.Status.NotJoined ||
            raffle.status == RaffleEntity.Status.Joined
        val storyId = raffle.stories.firstOrNull()?.id ?: return
        if (!running || storyId in shownStoryIds || settingsRepository.isStoriesViewed(storyId)) {
            return
        }
        if (!session.tryAcquire(walletId, StoryAutoShowSession.Owner.Raffle)) {
            return
        }
        // Mark only after a successful open so a failed show can retry.
        var shown = false
        try {
            shown = show(raffle.id)
        } finally {
            if (shown) {
                shownStoryIds.add(storyId)
            } else {
                session.release(walletId, StoryAutoShowSession.Owner.Raffle)
            }
        }
    }

    private suspend fun awaitStartedRaffle(walletId: String): RaffleEntity? {
        var crossedStart = false
        while (true) {
            val raffle = raffleRepository.getRaffle(walletId, raffleId = null, forced = crossedStart)
                ?: return null
            val now = RaffleClock.now()
            if (raffle.hasStarted(now)) {
                return raffle
            }
            if (crossedStart) {
                return null
            }
            delay(Duration.between(now, raffle.startsAt).toMillis().coerceAtLeast(0))
            crossedStart = true
        }
    }
}
