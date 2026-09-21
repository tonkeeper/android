package com.tonapps.portfolio.screens.raffle

import androidx.compose.runtime.Immutable
import com.tonapps.bus.generated.Events.MysteryRaffle.MysteryRaffleAction
import com.tonapps.bus.generated.Events.MysteryRaffle.MysteryRaffleKind
import com.tonapps.wallet.data.raffle.entities.RaffleEntity
import java.time.OffsetDateTime

@Immutable
sealed interface RaffleScreenState {
    data object Loading : RaffleScreenState
    data object Error : RaffleScreenState

    @Immutable
    data class Content(
        val raffle: RaffleEntity,
        val kind: RaffleKind,
        val items: List<RaffleListItem>,
        val earnHeaderIndex: Int,
        val participating: Boolean,
        val showGetMore: Boolean,
        val analyticsKind: MysteryRaffleKind,
        val ctaAction: MysteryRaffleAction,
        val ticketsTotal: Int,
        val prize: String?,
        val nextPhaseBoundary: OffsetDateTime? = null,
    ) : RaffleScreenState
}

enum class RaffleKind {
    Offer, Active, Result
}
