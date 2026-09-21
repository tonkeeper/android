package com.tonapps.portfolio.screens.raffle

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.tonapps.wallet.data.raffle.entities.RaffleEntity
import java.time.OffsetDateTime
import ui.components.moon.cell.MoonBundlePosition

/** One row of the raffle screen list; built by [RaffleFeature] from the payload. */
@Immutable
sealed class RaffleListItem(val key: String) {

    data object Hero : RaffleListItem("hero")

    data class Countdown(val target: OffsetDateTime) : RaffleListItem("countdown")

    data class RevealDate(val date: OffsetDateTime) : RaffleListItem("reveal_date")

    data object Tickets : RaffleListItem("tickets")

    data class SectionHeader(@StringRes val res: Int) : RaffleListItem("header_$res")

    /** Group label that comes as text from the backend (benefit card sections). */
    data class BenefitHeader(val text: String) : RaffleListItem("header_benefit_$text")

    data class Benefit(
        val card: RaffleEntity.BenefitCard,
        val first: Boolean,
    ) : RaffleListItem("benefit_${card.id}")

    data object PrizesRow : RaffleListItem("prizes_row")

    data class Task(
        val task: RaffleEntity.Task,
        val pos: MoonBundlePosition,
    ) : RaffleListItem("task_${task.id}")

    data class Milestone(
        val milestone: RaffleEntity.Milestone,
        val pos: MoonBundlePosition,
        val isCurrent: Boolean,
    ) : RaffleListItem("milestone_${milestone.id}")

    data class History(
        val entry: RaffleEntity.HistoryItem,
        val pos: MoonBundlePosition,
    ) : RaffleListItem("history_${entry.id}")

    data class Faq(
        val id: String,
        @StringRes val question: Int,
        @StringRes val answer: Int,
        val pos: MoonBundlePosition = MoonBundlePosition.Default,
        val questionDate: OffsetDateTime? = null,
        val answerDate: OffsetDateTime? = null,
        val answerTickets: Int? = null,
    ) : RaffleListItem("faq_$id")
}
