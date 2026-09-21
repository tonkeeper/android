package com.tonapps.portfolio.screens.raffle

import com.tonapps.bus.generated.Events.MysteryRaffle.MysteryRaffleAction
import com.tonapps.bus.generated.Events.MysteryRaffle.MysteryRaffleKind
import com.tonapps.core.deeplink.isMigrateDeeplink
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.mvi.flow.mapLatestCatching
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.raffle.RaffleClock
import com.tonapps.wallet.data.raffle.RaffleRepository
import com.tonapps.wallet.data.tx.TransactionManager
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.data.raffle.entities.RaffleEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.random.Random
import ui.components.moon.cell.defaultBundleType

private const val BOUNDARY_REFRESH_JITTER_MS = 30_000L
private const val GET_MORE_MIN_EARN_ITEMS = 2
private const val FALLBACK_MIGRATION_TICKETS = 10

class RaffleFeature(
    private val walletId: String,
    private val raffleId: String?,
    private val raffleRepository: RaffleRepository,
    private val accountRepository: AccountRepository,
    private val transactionManager: TransactionManager,
) : AsyncViewModel() {

    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    // Finalized on-chain events may grant tickets, so they refresh the open
    // screen too. The SSE stream covers only the selected wallet, so the
    // refresh applies just when this screen shows that same wallet.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val onChainEvents = accountRepository.selectedWalletFlow
        .flatMapLatest { wallet ->
            transactionManager.eventsFlow(wallet)
                .filter { !it.pending }
                .map { wallet.id }
        }

    val state: StateFlow<RaffleScreenState> = merge(
        flowOf(false),
        refreshSignal.map { true },
        onChainEvents.filter { it == walletId }.map { true },
    )
        .mapLatestCatching { forced ->
            raffleRepository.getRaffle(walletId, raffleId, forced)
        }
        // Keep showing the loaded raffle if a follow-up refresh fails
        .runningFold(RaffleScreenState.Loading as RaffleScreenState) { previous, raffle ->
            when {
                raffle != null -> raffle.toContent()
                previous is RaffleScreenState.Content -> previous
                else -> RaffleScreenState.Error
            }
        }
        .cacheState(initialValue = RaffleScreenState.Loading)

    init {
        bgScope.launch {
            state
                .map { (it as? RaffleScreenState.Content)?.nextPhaseBoundary }
                .distinctUntilChanged()
                .collectLatest { boundary ->
                    boundary ?: return@collectLatest
                    // The boundary is campaign-wide, so every open screen would re-fetch in the
                    // same second; the random delay spreads that thundering herd over 30s
                    // (mirrors the iOS boundary refresh in MysteryRaffleCoordinator).
                    while (true) {
                        val untilBoundary = Duration.between(RaffleClock.now(), boundary).toMillis()
                        delay(untilBoundary.coerceAtLeast(0) + Random.nextLong(BOUNDARY_REFRESH_JITTER_MS))
                        refresh()
                    }
                }
        }
    }

    fun refresh() {
        refreshSignal.tryEmit(Unit)
    }

    private fun RaffleEntity.toAnalyticsKind(kind: RaffleKind): MysteryRaffleKind = when (status) {
        RaffleEntity.Status.Won -> MysteryRaffleKind.Won
        RaffleEntity.Status.Lost -> MysteryRaffleKind.Lost
        else -> if (kind == RaffleKind.Offer) { MysteryRaffleKind.Migration } else { MysteryRaffleKind.Active }
    }

    private fun MysteryRaffleKind.toCtaAction(): MysteryRaffleAction = when (this) {
        MysteryRaffleKind.Won, MysteryRaffleKind.Lost -> MysteryRaffleAction.ResultsLink
        MysteryRaffleKind.Migration -> MysteryRaffleAction.Migrate
        MysteryRaffleKind.Active -> MysteryRaffleAction.Swap
    }

    private fun RaffleEntity.toKind(): RaffleKind = when (status) {
        RaffleEntity.Status.Won, RaffleEntity.Status.Lost -> RaffleKind.Result
        RaffleEntity.Status.EndedPending -> RaffleKind.Active
        RaffleEntity.Status.NotJoined, RaffleEntity.Status.Joined ->
            if (isOfferPhase) { RaffleKind.Offer } else { RaffleKind.Active }
    }

    private fun RaffleEntity.nextPhaseBoundary(
        now: OffsetDateTime,
        countdownTarget: OffsetDateTime?,
    ): OffsetDateTime? {
        val statusEdge = when (status) {
            RaffleEntity.Status.NotJoined, RaffleEntity.Status.Joined -> endsAt
            RaffleEntity.Status.EndedPending -> prizesRevealAt ?: endsAt
            RaffleEntity.Status.Won, RaffleEntity.Status.Lost -> null
        }
        if (statusEdge != null && !statusEdge.isAfter(now)) {
            return statusEdge
        }
        return listOfNotNull(endsAt, prizesRevealAt, progress?.zeroFeeEndsAt)
            .filter { it.isAfter(now) && it != countdownTarget }
            .minOrNull()
    }

    private fun RaffleEntity.migrationTickets(): Int =
        tasks.firstOrNull { it.deeplink?.let(::isMigrateDeeplink) == true }?.rewardTickets
            ?: FALLBACK_MIGRATION_TICKETS

    private fun RaffleEntity.faqItems(kind: RaffleKind, now: OffsetDateTime): List<RaffleListItem.Faq> {
        val entries = when {
            kind == RaffleKind.Offer -> {
                val zeroFeeEndsAt = progress?.zeroFeeEndsAt ?: return emptyList()
                val launchDate = prizesRevealAt ?: zeroFeeEndsAt
                listOf(
                    RaffleListItem.Faq(
                        id = "about",
                        question = Localization.raffle_faq_about_q,
                        answer = Localization.raffle_faq_about_a,
                        answerDate = launchDate,
                    ),
                    RaffleListItem.Faq(
                        id = "migrate_before",
                        question = Localization.raffle_faq_migrate_before_q,
                        answer = Localization.raffle_faq_migrate_before_a,
                        questionDate = launchDate,
                    ),
                    RaffleListItem.Faq(
                        id = "migrate_after",
                        question = Localization.raffle_faq_migrate_after_q,
                        answer = Localization.raffle_faq_migrate_after_a,
                        questionDate = launchDate,
                    ),
                    RaffleListItem.Faq(
                        id = "fee_end",
                        question = Localization.raffle_faq_fee_end_q,
                        answer = Localization.raffle_faq_fee_end_a,
                        answerDate = zeroFeeEndsAt,
                    ),
                )
            }

            kind == RaffleKind.Active &&
                status != RaffleEntity.Status.EndedPending &&
                endsAt.isAfter(now) -> listOf(
                RaffleListItem.Faq(
                    id = "earn",
                    question = Localization.raffle_faq_earn_q,
                    answer = Localization.raffle_faq_earn_a,
                    answerTickets = migrationTickets(),
                ),
                RaffleListItem.Faq(
                    id = "prizes",
                    question = Localization.raffle_faq_prizes_q,
                    answer = Localization.raffle_faq_prizes_a,
                ),
                RaffleListItem.Faq(
                    id = "end",
                    question = Localization.raffle_faq_end_q,
                    answer = Localization.raffle_faq_end_a,
                    answerDate = endsAt,
                ),
                RaffleListItem.Faq(
                    id = "winners",
                    question = Localization.raffle_faq_winners_q,
                    answer = Localization.raffle_faq_winners_a,
                ),
                RaffleListItem.Faq(
                    id = "rewards",
                    question = Localization.raffle_faq_rewards_q,
                    answer = Localization.raffle_faq_rewards_a,
                ),
            )

            else -> emptyList()
        }
        return entries.mapIndexed { index, faq ->
            faq.copy(pos = defaultBundleType(entries.size, index))
        }
    }

    private fun RaffleEntity.toContent(): RaffleScreenState.Content {
        val now = RaffleClock.now()
        val kind = toKind()
        val analyticsKind = toAnalyticsKind(kind)

        val countdownTarget: OffsetDateTime?
        val revealDate: OffsetDateTime?
        val showTickets: Boolean
        val showEarnSections: Boolean
        val faqAfterBenefits: Boolean
        when (kind) {
            RaffleKind.Active -> {
                // ended_pending keeps the Active layout, but earning is over,
                // so the tasks/milestones sections disappear with it.
                val earning = status != RaffleEntity.Status.EndedPending
                countdownTarget = endsAt.takeIf { earning && it.isAfter(now) }
                revealDate = null
                showTickets = true
                showEarnSections = earning
                faqAfterBenefits = false
            }

            RaffleKind.Offer -> {
                countdownTarget = progress?.zeroFeeEndsAt
                    ?.takeIf { status == RaffleEntity.Status.NotJoined && it.isAfter(now) }
                revealDate = prizesRevealAt
                    ?.takeIf { status == RaffleEntity.Status.Joined && it.isAfter(now) }
                showTickets = false
                showEarnSections = true
                faqAfterBenefits = true
            }

            RaffleKind.Result -> {
                countdownTarget = null
                revealDate = null
                showTickets = true
                showEarnSections = false
                faqAfterBenefits = false
            }
        }

        val dedupedTasks = tasks.distinctBy { it.id }
        val dedupedMilestones = milestones.distinctBy { it.id }
        val faqItems = faqItems(kind, now)
        var earnHeaderIndex = -1
        val items = buildList {
            fun addFaqSection() {
                if (faqItems.isEmpty()) {
                    return
                }
                add(RaffleListItem.SectionHeader(Localization.raffle_faq))
                addAll(faqItems)
            }
            add(RaffleListItem.Hero)
            if (countdownTarget != null) {
                add(RaffleListItem.Countdown(countdownTarget))
            }
            if (revealDate != null) {
                add(RaffleListItem.RevealDate(revealDate))
            }
            if (showTickets) {
                add(RaffleListItem.Tickets)
            }
            benefitCards.distinctBy { it.id }.forEachIndexed { index, card ->
                add(RaffleListItem.Benefit(card, first = index == 0))
            }
            if (faqAfterBenefits) {
                addFaqSection()
            }
            if (showEarnSections) {
                if (prizes.isNotEmpty()) {
                    add(RaffleListItem.BenefitHeader(prizesHeader))
                    add(RaffleListItem.PrizesRow)
                }
                if (dedupedTasks.isNotEmpty()) {
                    earnHeaderIndex = size
                    add(RaffleListItem.SectionHeader(Localization.raffle_how_to_earn))
                    dedupedTasks.forEachIndexed { index, task ->
                        add(RaffleListItem.Task(task, defaultBundleType(dedupedTasks.size, index)))
                    }
                }
                if (dedupedMilestones.isNotEmpty()) {
                    if (earnHeaderIndex < 0) {
                        earnHeaderIndex = size
                    }
                    add(RaffleListItem.SectionHeader(Localization.raffle_milestones))
                    val currentMilestoneId = dedupedMilestones.firstOrNull { !it.done }?.id
                    dedupedMilestones.forEachIndexed { index, milestone ->
                        add(
                            RaffleListItem.Milestone(
                                milestone = milestone,
                                pos = defaultBundleType(dedupedMilestones.size, index),
                                isCurrent = milestone.id == currentMilestoneId,
                            )
                        )
                    }
                }
            }
            val history = progress?.history.orEmpty().distinctBy { it.id }
            if (history.isNotEmpty()) {
                add(RaffleListItem.SectionHeader(Localization.raffle_history))
                history.forEachIndexed { index, entry ->
                    add(RaffleListItem.History(entry, defaultBundleType(history.size, index)))
                }
            }
            if (!faqAfterBenefits) {
                addFaqSection()
            }
        }
        // ended_pending keeps the Active layout, but earning tickets is over
        val participating = status == RaffleEntity.Status.NotJoined || status == RaffleEntity.Status.Joined
        val showGetMore = participating &&
            kind == RaffleKind.Active &&
            earnHeaderIndex >= 0 &&
            dedupedTasks.size + dedupedMilestones.size >= GET_MORE_MIN_EARN_ITEMS
        return RaffleScreenState.Content(
            raffle = this,
            kind = kind,
            items = items,
            earnHeaderIndex = earnHeaderIndex,
            participating = participating,
            showGetMore = showGetMore,
            analyticsKind = analyticsKind,
            ctaAction = analyticsKind.toCtaAction(),
            ticketsTotal = progress?.ticketsTotal ?: 0,
            prize = progress?.winningPrize?.title?.takeIf { analyticsKind == MysteryRaffleKind.Won },
            nextPhaseBoundary = nextPhaseBoundary(now, countdownTarget),
        )
    }
}
