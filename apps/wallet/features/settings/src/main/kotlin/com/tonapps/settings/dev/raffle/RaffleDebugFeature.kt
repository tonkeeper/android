package com.tonapps.settings.dev.raffle

import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.raffle.RaffleRepository
import com.tonapps.wallet.data.raffle.debug.RaffleDebugStore
import com.tonapps.wallet.data.raffle.entities.RaffleEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.OffsetDateTime

enum class RafflePhase(val title: String) {
    RealTime("Real time (no override)"),
    Started("Just started"),
    MidCampaign("Mid-campaign"),
    AwaitingDraw("Ended, awaiting draw"),
    Results("Results revealed"),
}

class RaffleDebugFeature(
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val raffleRepository: RaffleRepository,
    private val debugStore: RaffleDebugStore,
    private val settingsRepository: SettingsRepository,
) : AsyncViewModel() {

    val debugNow: StateFlow<OffsetDateTime?> = debugStore.debugNowFlow
    val raffle: StateFlow<RaffleEntity?> field = MutableStateFlow(null)
    val prizes: StateFlow<List<RaffleEntity.Prize>> field = MutableStateFlow(emptyList())
    val message: StateFlow<String?> field = MutableStateFlow(null)

    private var walletId: String? = null

    private val lang: String
        get() = settingsRepository.getLocale().language

    init {
        execute { load() }
    }

    fun setPhase(phase: RafflePhase) = execute {
        debugStore.debugNow = when (phase) {
            RafflePhase.RealTime -> null
            RafflePhase.Started -> requireRaffle().startsAt.plusMinutes(1)
            RafflePhase.MidCampaign -> requireRaffle().let { middle(it.startsAt, it.endsAt) }
            RafflePhase.AwaitingDraw -> requireRaffle().let { middle(it.endsAt, it.revealAt()) }
            RafflePhase.Results -> requireRaffle().revealAt().plusHours(1)
        }
        refresh()
        phase.title
    }

    fun win(prize: RaffleEntity.Prize) = execute {
        val raffle = requireRaffle()
        val walletId = requireNotNull(walletId)
        debugStore.pickWinner(walletId, raffle.id, prize.id)
        debugStore.debugNow = raffle.revealAt().plusHours(1)
        refresh()
        "Won ${prize.title}"
    }

    fun lose() = execute {
        val raffle = requireRaffle()
        val walletId = requireNotNull(walletId)
        debugStore.pickWinner(walletId, raffle.id, prizeId = null)
        debugStore.debugNow = raffle.revealAt().plusHours(1)
        refresh()
        "Winner row cleared (lost with tickets)"
    }

    fun resetStoryViews() = execute {
        settingsRepository.resetStoriesViewed()
        debugStore.resetStoryDedupe()
        "Story view flags reset"
    }

    private suspend fun load(): String {
        val wallet = unifiedAccountRepository.getSelectedWallet()
        if (wallet == null || wallet.type != WalletType.Multichain) {
            return "Select a multichain wallet first"
        }
        walletId = wallet.id
        val current = debugStore.raffleAt(wallet.id, lang, at = null)
            ?: return "Backend returned no raffle"
        raffle.value = current
        prizes.value = current.prizes.ifEmpty {
            debugStore.raffleAt(wallet.id, lang, at = middle(current.startsAt, current.endsAt))
                ?.prizes
                .orEmpty()
        }
        return "Raffle ${current.id}, status ${current.status}"
    }

    private suspend fun refresh() {
        walletId?.let { raffleRepository.prefetch(it) }
    }

    private fun execute(action: suspend () -> String) {
        bgScope.launch {
            try {
                message.value = action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message.value = e.message ?: "Request failed"
            }
        }
    }

    private fun requireRaffle(): RaffleEntity = requireNotNull(raffle.value) { "No raffle loaded" }

    private fun RaffleEntity.revealAt(): OffsetDateTime = prizesRevealAt ?: endsAt

    private fun middle(from: OffsetDateTime, to: OffsetDateTime): OffsetDateTime =
        from.plus(Duration.between(from, to).dividedBy(2))
}
