package com.tonapps.wallet.data.raffle.entities

import java.time.OffsetDateTime

/** Domain view of one raffle campaign; the generated API models stay inside the repository. */
data class RaffleEntity(
    val id: String,
    val status: Status,
    val hero: Hero,
    val title: String,
    val subtitle: String,
    val startsAt: OffsetDateTime,
    val endsAt: OffsetDateTime,
    val compactBanner: CompactBanner,
    val prizesHeader: String,
    val prizes: List<Prize>,
    val tasks: List<Task>,
    val milestones: List<Milestone>,
    val cta: Cta,
    val prizesRevealAt: OffsetDateTime? = null,
    val statusBadge: String? = null,
    val benefitCards: List<BenefitCard> = emptyList(),
    val banner: Banner? = null,
    val stories: List<Story> = emptyList(),
    val progress: Progress? = null,
) {

    val isOfferPhase: Boolean
        get() = (status == Status.NotJoined || status == Status.Joined) &&
            prizes.isEmpty() && milestones.isEmpty()

    fun hasStarted(now: OffsetDateTime): Boolean = !startsAt.isAfter(now)

    enum class Status {
        NotJoined, Joined, EndedPending, Won, Lost
    }

    data class Hero(
        val image: String,
        val badgeIconId: String? = null,
    )

    data class CompactBanner(
        val defaultTitle: String,
        val activeTitle: String,
        val iconId: String,
    )

    data class Banner(
        val title: String,
        val imageUrl: String,
        val button: Cta,
        val description: String? = null,
    )

    data class Cta(
        val title: String,
        val action: Action,
        val payload: String,
    ) {
        enum class Action {
            Deeplink, Link
        }
    }

    data class Prize(
        val id: String,
        val image: String,
        val title: String,
        val subtitle: String? = null,
    )

    data class Task(
        val id: String,
        val iconId: String,
        val title: String,
        val rewardTickets: Int,
        val subtitle: String? = null,
        val deeplink: String? = null,
        val done: Boolean = false,
    )

    data class Milestone(
        val id: String,
        val iconId: String,
        val title: String,
        val rewardTickets: Int,
        val subtitle: String? = null,
        val done: Boolean = false,
    )

    data class BenefitCard(
        val id: String,
        val iconId: String,
        val label: String,
        val title: String,
        val imageUrl: String? = null,
        val subtitle: String? = null,
    )

    data class Story(
        val id: String,
        val pages: List<StoryPage>,
    )

    data class StoryPage(
        val title: String,
        val description: String,
        val image: String,
        val buttons: List<Cta> = emptyList(),
    )

    data class Progress(
        val ticketsTotal: Int,
        val history: List<HistoryItem>,
        val winningPrize: WinningPrize? = null,
        val prizeDeliveryDays: Int? = null,
        val zeroFeeEndsAt: OffsetDateTime? = null,
    )

    data class HistoryItem(
        val id: String,
        val awardedAt: OffsetDateTime,
        val iconId: String,
        val title: String,
        val tickets: Int,
    )

    data class WinningPrize(
        val title: String,
        val image: String,
    )
}
