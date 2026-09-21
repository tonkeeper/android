package com.tonapps.wallet.data.raffle

import com.tonapps.wallet.data.raffle.entities.RaffleEntity
import io.walletapi.models.Raffle
import io.walletapi.models.RaffleBanner
import io.walletapi.models.RaffleBenefitCard
import io.walletapi.models.RaffleCTA
import io.walletapi.models.RaffleMilestone
import io.walletapi.models.RafflePrize
import io.walletapi.models.RaffleProgress
import io.walletapi.models.RaffleStatus
import io.walletapi.models.RaffleStory
import io.walletapi.models.RaffleTask

internal fun Raffle.toEntity() = RaffleEntity(
    id = id,
    status = status.toEntity(),
    hero = RaffleEntity.Hero(image = hero.image, badgeIconId = hero.badgeIconId),
    title = title,
    subtitle = subtitle,
    startsAt = startsAt,
    endsAt = endsAt,
    compactBanner = RaffleEntity.CompactBanner(
        defaultTitle = compactBanner.defaultTitle,
        activeTitle = compactBanner.activeTitle,
        iconId = compactBanner.iconId,
    ),
    prizesHeader = prizesHeader,
    prizes = prizes.map { it.toEntity() },
    tasks = tasks.map { it.toEntity() },
    milestones = milestones.map { it.toEntity() },
    cta = cta.toEntity(),
    prizesRevealAt = prizesRevealAt,
    statusBadge = statusBadge,
    benefitCards = benefitCards.orEmpty().map { it.toEntity() },
    banner = banner?.toEntity(),
    stories = stories.orEmpty().map { it.toEntity() },
    progress = progress?.toEntity(),
)

private fun RaffleStatus.toEntity() = when (this) {
    RaffleStatus.not_joined -> RaffleEntity.Status.NotJoined
    RaffleStatus.joined -> RaffleEntity.Status.Joined
    RaffleStatus.ended_pending -> RaffleEntity.Status.EndedPending
    RaffleStatus.won -> RaffleEntity.Status.Won
    RaffleStatus.lost -> RaffleEntity.Status.Lost
}

private fun RaffleCTA.toEntity() = RaffleEntity.Cta(
    title = title,
    action = when (action) {
        RaffleCTA.Action.deeplink -> RaffleEntity.Cta.Action.Deeplink
        RaffleCTA.Action.link -> RaffleEntity.Cta.Action.Link
    },
    payload = payload,
)

private fun RaffleBanner.toEntity() = RaffleEntity.Banner(
    title = title,
    imageUrl = imageUrl,
    button = button.toEntity(),
    description = description,
)

private fun RafflePrize.toEntity() = RaffleEntity.Prize(
    id = id,
    image = image,
    title = title,
    subtitle = subtitle,
)

private fun RaffleTask.toEntity() = RaffleEntity.Task(
    id = id,
    iconId = iconId,
    title = title,
    rewardTickets = rewardTickets,
    subtitle = subtitle,
    deeplink = deeplink,
    done = done == true,
)

private fun RaffleMilestone.toEntity() = RaffleEntity.Milestone(
    id = id,
    iconId = iconId,
    title = title,
    rewardTickets = rewardTickets,
    subtitle = subtitle,
    done = done == true,
)

private fun RaffleBenefitCard.toEntity() = RaffleEntity.BenefitCard(
    id = id,
    iconId = iconId,
    label = label,
    title = title,
    imageUrl = imageUrl,
    subtitle = subtitle,
)

private fun RaffleStory.toEntity() = RaffleEntity.Story(
    id = id,
    pages = pages.map { page ->
        RaffleEntity.StoryPage(
            title = page.title,
            description = page.description,
            image = page.image,
            buttons = page.buttons.orEmpty().map { it.toEntity() },
        )
    },
)

private fun RaffleProgress.toEntity() = RaffleEntity.Progress(
    ticketsTotal = ticketsTotal,
    history = history.map {
        RaffleEntity.HistoryItem(
            id = it.id,
            awardedAt = it.awardedAt,
            iconId = it.iconId,
            title = it.title,
            tickets = it.tickets,
        )
    },
    winningPrize = winningPrize?.let { RaffleEntity.WinningPrize(title = it.title, image = it.image) },
    prizeDeliveryDays = prizeDeliveryDays,
    zeroFeeEndsAt = zeroFeeEndsAt,
)
