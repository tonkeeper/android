package com.tonapps.portfolio.screens.raffle

import android.text.format.DateFormat
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.apps.wallet.features.portfolio.R
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.bus.generated.Events.MysteryRaffle.MysteryRaffleAction
import com.tonapps.bus.generated.Events.MysteryRaffle.MysteryRaffleKind
import com.tonapps.bus.generated.Events.MysteryRaffle.MysteryRaffleSource
import com.tonapps.portfolio.screens.raffle.components.RaffleConfetti
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.localization.Plurals
import com.tonapps.wallet.data.raffle.RaffleClock
import com.tonapps.wallet.data.raffle.entities.RaffleEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.ButtonSizeSmall
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonActionIcon
import ui.components.moon.MoonAsyncImage
import ui.components.moon.MoonExpandable
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonLoader
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.TextCell
import ui.components.moon.container.MoonSurface
import ui.components.moon.screen.MoonEmptyScreen
import ui.components.moon.screen.MoonEmptyScreenType
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RAFFLE_LIST_BOTTOM_PADDING = 104.dp

private const val RAFFLE_DEEPLINK_FROM = "raffle"

// Figma 8305:43944: tapping the next (first not-done) milestone leads into swap to build
// volume; milestones carry no deeplink in the API, so the destination lives client-side.
private const val MILESTONE_SWAP_DEEPLINK = "tonkeeper://swap"
private val MILESTONE_CONNECTOR_CLEARANCE = 4.dp
private val MILESTONE_CONNECTOR_STROKE = 2.dp

private val MILESTONE_DIVIDER_EXTRA_INSET = 60.dp
private val CTA_FADE_HEIGHT = 72.dp

// Overshoot past the icon bounds; MoonBundleCell's clip trims it at the cell edge.
private val MILESTONE_CONNECTOR_OVERSHOOT = 48.dp

private val RAFFLE_CELL_MIN_HEIGHT = 76.dp
private val RAFFLE_CELL_EXTRA_PADDING = 8.dp

private val RESULT_HERO_TOP_PADDING = 56.dp
private const val RESULT_HERO_ASPECT_RATIO = 390f / 228f
private const val HERO_ASPECT_RATIO = 390f / 276f

private val BENEFIT_CARD_HEIGHT = 136.dp
private val BENEFIT_CARD_IMAGE_WIDTH = 134.dp
private val BENEFIT_CARD_TOP_SPACING = 32.dp
private val BENEFIT_CARD_SPACING = 12.dp

@Composable
fun RaffleScreen(
    walletId: String,
    raffleId: String?,
    source: MysteryRaffleSource,
    onClose: () -> Unit,
    onOpenDeeplink: (String) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val feature = koinViewModel<RaffleFeature> { parametersOf(walletId, raffleId) }
    val state by feature.state.collectAsState()
    RaffleScreenInternal(
        state = state,
        source = source,
        onClose = onClose,
        onRefresh = { feature.refresh() },
        onOpenDeeplink = onOpenDeeplink,
        onOpenLink = onOpenLink,
    )
}

@Composable
internal fun RaffleScreenInternal(
    state: RaffleScreenState,
    source: MysteryRaffleSource,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onOpenDeeplink: (String) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    MoonSurface(modifier = Modifier.fillMaxSize()) {
        when (state) {
            is RaffleScreenState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                MoonLoader()
            }

            is RaffleScreenState.Error -> MoonEmptyScreen(
                text = stringResource(Localization.something_went_wrong),
                type = MoonEmptyScreenType.Error,
                buttonText = stringResource(Localization.retry),
                onButtonClick = onRefresh,
            )

            is RaffleScreenState.Content -> RaffleContent(
                content = state,
                source = source,
                onClose = onClose,
                onRefresh = onRefresh,
                onOpenDeeplink = onOpenDeeplink,
                onOpenLink = onOpenLink,
            )
        }
    }
}

@Composable
private fun RaffleContent(
    content: RaffleScreenState.Content,
    source: MysteryRaffleSource,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onOpenDeeplink: (String) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val raffle = content.raffle
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val events = AnalyticsHelper.Default.events.mysteryRaffle
    val analyticsKind = content.analyticsKind
    val ticketsTotal = content.ticketsTotal

    LaunchedEffect(Unit) {
        events.raffleOpen(
            source = source,
            kind = analyticsKind,
            ticketsTotal = ticketsTotal,
            prize = content.prize,
        )
    }
    val locale = LocalConfiguration.current.locales[0]
    val historyFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("MMMM d, HH:mm", locale)
    }
    val faqDateFormatter = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMMd"), locale)
    }
    var expandedFaq by remember { mutableStateOf(emptySet<String>()) }
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(rememberNestedScrollInteropConnection()),
            contentPadding = PaddingValues(bottom = navBottom + RAFFLE_LIST_BOTTOM_PADDING),
        ) {
            items(
                items = content.items,
                key = { it.key },
                contentType = { it::class },
            ) { item ->
                when (item) {
                    is RaffleListItem.Hero -> HeroSection(raffle, content.kind)
                    is RaffleListItem.Countdown -> CountdownRow(item.target, onRefresh)
                    is RaffleListItem.RevealDate -> RevealDateRow(item.date)
                    is RaffleListItem.Tickets -> TicketsCard(
                        tickets = raffle.progress?.ticketsTotal ?: 0,
                        isFinal = !content.participating,
                        showGetMore = content.showGetMore,
                        onGetMore = {
                            events.raffleClickGetMore(analyticsKind, ticketsTotal)
                            scope.launch { listState.animateScrollToItem(content.earnHeaderIndex) }
                        },
                    )

                    is RaffleListItem.SectionHeader -> GroupHeader(stringResource(item.res))
                    is RaffleListItem.BenefitHeader -> GroupHeader(item.text)
                    is RaffleListItem.Benefit -> BenefitCell(item.card, item.first)
                    is RaffleListItem.PrizesRow -> PrizesRow(raffle.prizes)
                    is RaffleListItem.Task -> TaskCell(
                        task = item.task,
                        position = item.pos,
                        onOpenDeeplink = onOpenDeeplink,
                        onClickTask = { events.raffleClickTask(item.task.id) },
                    )
                    is RaffleListItem.Milestone -> MilestoneCell(
                        milestone = item.milestone,
                        position = item.pos,
                        isCurrent = item.isCurrent,
                        onOpenDeeplink = onOpenDeeplink,
                        onClickMilestone = { events.raffleClickMilestone(item.milestone.id) },
                    )
                    is RaffleListItem.History -> HistoryCell(item.entry, item.pos, historyFormatter)
                    is RaffleListItem.Faq -> FaqCell(
                        item = item,
                        formatter = faqDateFormatter,
                        expanded = item.key in expandedFaq,
                        onToggle = {
                            expandedFaq = if (item.key in expandedFaq) {
                                expandedFaq - item.key
                            } else {
                                expandedFaq + item.key
                            }
                        },
                    )
                }
            }
        }

        MoonActionIcon(
            painter = painterResource(UIKitIcon.ic_close_16),
            onClick = onClose,
            tintColor = UIKit.colorScheme.icon.secondary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        )

        val pageColor = UIKit.colorScheme.background.page
        val ctaFadeEnd = with(LocalDensity.current) { CTA_FADE_HEIGHT.toPx() }
        CtaButton(
            cta = raffle.cta,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(pageColor.copy(alpha = 0f), pageColor),
                        endY = ctaFadeEnd,
                    ),
                )
                .padding(16.dp)
                .navigationBarsPadding(),
            onClickCta = {
                events.raffleClickCta(content.ctaAction, analyticsKind, ticketsTotal)
            },
            onOpenDeeplink = onOpenDeeplink,
            onOpenLink = onOpenLink,
        )

        if (raffle.status == RaffleEntity.Status.Won) {
            RaffleConfetti(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text = text,
        style = UIKit.typography.label1,
        color = UIKit.colorScheme.text.primary,
        modifier = Modifier.padding(
            start = 16.dp,
            end = 16.dp,
            top = 28.dp,
            bottom = 12.dp,
        ),
    )
}

@Composable
private fun HeroSection(raffle: RaffleEntity, kind: RaffleKind) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val isResult = kind == RaffleKind.Result
        val heroFallback = if (isResult) { null } else { painterResource(R.drawable.raffle_hero) }
        MoonAsyncImage(
            image = raffle.hero.image,
            placeholder = heroFallback,
            error = heroFallback,
            contentScale = if (isResult) { ContentScale.Fit } else { ContentScale.Crop },
            modifier = Modifier
                .fillMaxWidth()
                .run {
                    if (isResult) {
                        padding(top = RESULT_HERO_TOP_PADDING).aspectRatio(RESULT_HERO_ASPECT_RATIO)
                    } else {
                        aspectRatio(HERO_ASPECT_RATIO)
                    }
                },
        )
        if (!isResult) {
            Spacer(modifier = Modifier.height(16.dp))
        }

        raffle.statusBadge?.takeIf { kind != RaffleKind.Result }?.let { badge ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(UIKit.colorScheme.accent.blue.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                MoonItemIcon(
                    painter = painterResource(heroBadgeIconRes(raffle, kind)),
                    size = 18.dp,
                    color = UIKit.colorScheme.accent.blue,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = badge,
                    style = UIKit.typography.label2,
                    color = UIKit.colorScheme.accent.blue,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(
            text = raffle.title,
            style = UIKit.typography.h2,
            color = UIKit.colorScheme.text.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = raffle.subtitle,
            style = UIKit.typography.body1,
            color = UIKit.colorScheme.text.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

@DrawableRes
private fun heroBadgeIconRes(raffle: RaffleEntity, kind: RaffleKind): Int =
    raffleBadgeIconResOrNull(raffle.hero.badgeIconId) ?: when {
        kind == RaffleKind.Offer && raffle.status == RaffleEntity.Status.Joined ->
            UIKitIcon.ic_checkmark_circle_32
        kind == RaffleKind.Offer -> UIKitIcon.ic_fire_badge_16
        else -> UIKitIcon.ic_ticket_28
    }

@Composable
private fun CountdownRow(target: OffsetDateTime, onExpired: () -> Unit) {
    var remaining by remember(target) {
        mutableStateOf(Duration.between(RaffleClock.now(), target))
    }
    LaunchedEffect(target) {
        while (true) {
            val value = Duration.between(RaffleClock.now(), target)
            if (value.isNegative || value.isZero) {
                remaining = Duration.ZERO
                onExpired()
                break
            }
            remaining = value
            delay(1_000)
        }
    }
    // The refresh triggered by onExpired decides what an ended raffle looks
    // like; never keep a frozen 00:00:00 on screen while it is in flight.
    if (remaining.isZero || remaining.isNegative) {
        return
    }

    val days = remaining.toDays().toInt()
    val time = String.format(
        Locale.US,
        "%02d:%02d:%02d",
        remaining.toHours() % 24,
        remaining.toMinutes() % 60,
        remaining.seconds % 60,
    )
    val value = if (days > 0) {
        "${pluralStringResource(Plurals.raffle_days, days, days)} $time"
    } else {
        time
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoonItemIcon(
            painter = painterResource(UIKitIcon.ic_clock_28),
            size = 18.dp,
            color = UIKit.colorScheme.accent.blue,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(Localization.raffle_ends_in, value),
            style = UIKit.typography.body2.copy(fontFeatureSettings = "tnum"),
            color = UIKit.colorScheme.accent.blue,
        )
    }
}

@Composable
private fun RevealDateRow(date: OffsetDateTime) {
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { DateTimeFormatter.ofPattern("MMMM d", locale) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoonItemIcon(
            painter = painterResource(UIKitIcon.ic_clock_28),
            size = 18.dp,
            color = UIKit.colorScheme.accent.blue,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = stringResource(
                Localization.raffle_more_on,
                date.atZoneSameInstant(ZoneId.systemDefault()).format(formatter),
            ),
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.accent.blue,
        )
    }
}

@Composable
private fun TicketsCard(
    tickets: Int,
    isFinal: Boolean,
    showGetMore: Boolean,
    onGetMore: () -> Unit,
) {
    MoonBundleCell(
        margins = remember { PaddingValues(start = 16.dp, end = 16.dp, top = 32.dp) },
    ) {
        TextCell(
            title = tickets.toString(),
            subtitle = stringResource(
                if (isFinal) { Localization.raffle_final_tickets } else { Localization.raffle_your_tickets },
            ),
            minHeight = 72.dp,
            image = {
                RaffleIcon("ticket")
            },
            content = if (showGetMore) {
                {
                    MoonAccentButton(
                        text = stringResource(Localization.raffle_get_more),
                        size = ButtonSizeSmall,
                        onClick = onGetMore,
                    )
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun BenefitCell(card: RaffleEntity.BenefitCard, first: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = if (first) { BENEFIT_CARD_TOP_SPACING } else { BENEFIT_CARD_SPACING },
            )
            .height(BENEFIT_CARD_HEIGHT)
            .clip(UIKit.shapes.extraLarge)
            .background(UIKit.colorScheme.background.content),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MoonItemIcon(
                    painter = painterResource(raffleBadgeIconRes(card.iconId)),
                    size = 16.dp,
                    color = UIKit.colorScheme.accent.blue,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = card.label.uppercase(),
                    style = UIKit.typography.label3,
                    color = UIKit.colorScheme.accent.blue,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = card.title,
                style = UIKit.typography.label1,
                color = UIKit.colorScheme.text.primary,
                maxLines = 2,
            )
            card.subtitle?.let { subtitle ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = UIKit.typography.body2,
                    color = UIKit.colorScheme.text.secondary,
                    maxLines = 3,
                )
            }
        }
        card.imageUrl?.let { image ->
            MoonAsyncImage(
                image = image,
                modifier = Modifier
                    .width(BENEFIT_CARD_IMAGE_WIDTH)
                    .fillMaxHeight(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun PrizesRow(prizes: List<RaffleEntity.Prize>) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(prizes, key = { it.id }) { prize ->
            Column(
                modifier = Modifier
                    .width(176.dp)
                    .clip(UIKit.shapes.large)
                    .background(UIKit.colorScheme.background.content)
                    .padding(16.dp),
            ) {
                val prizeIcon = rafflePrizeIconResOrNull(prize.id)
                if (prizeIcon != null) {
                    Image(
                        painter = painterResource(prizeIcon),
                        contentDescription = null,
                    )
                } else {
                    MoonAsyncImage(
                        image = prize.image,
                        alignment = Alignment.CenterStart,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(UIKit.shapes.medium),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = prize.title,
                    style = UIKit.typography.label1,
                    color = UIKit.colorScheme.text.primary,
                    maxLines = 1,
                )
                prize.subtitle?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = UIKit.typography.body2,
                        color = UIKit.colorScheme.text.secondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskCell(
    task: RaffleEntity.Task,
    position: MoonBundlePosition,
    onOpenDeeplink: (String) -> Unit,
    onClickTask: () -> Unit,
) {
    val done = task.done
    MoonBundleCell(position = position) {
        TextCell(
            title = task.title,
            subtitle = task.subtitle,
            modifier = Modifier.padding(vertical = RAFFLE_CELL_EXTRA_PADDING),
            minHeight = RAFFLE_CELL_MIN_HEIGHT,
            maxLinesTitle = 2,
            maxLinesSubtitle = 2,
            image = {
                RaffleIcon(task.iconId)
            },
            content = {
                MoonItemIcon(
                    painter = painterResource(
                        if (done) { UIKitIcon.ic_done_bold_16 } else { UIKitIcon.ic_chevron_right_16 },
                    ),
                    size = 16.dp,
                    color = if (done) { UIKit.colorScheme.accent.blue } else { UIKit.colorScheme.icon.tertiary },
                )
            },
            onClick = task.deeplink?.takeIf { !done }?.let { deeplink ->
                {
                    onClickTask()
                    onOpenDeeplink(deeplink)
                }
            },
        )
    }
}

@Composable
private fun MilestoneCell(
    milestone: RaffleEntity.Milestone,
    position: MoonBundlePosition,
    isCurrent: Boolean,
    onOpenDeeplink: (String) -> Unit,
    onClickMilestone: () -> Unit,
) {
    val done = milestone.done
    val connectorColor = UIKit.colorScheme.background.contentTint
    MoonBundleCell(position = position, dividerExtraStartInset = MILESTONE_DIVIDER_EXTRA_INSET) {
        TextCell(
            title = milestone.title,
            subtitle = milestone.subtitle
                ?: pluralStringResource(
                    Plurals.raffle_tickets_reward,
                    milestone.rewardTickets,
                    milestone.rewardTickets,
                ),
            modifier = Modifier.padding(vertical = RAFFLE_CELL_EXTRA_PADDING),
            minHeight = RAFFLE_CELL_MIN_HEIGHT,
            image = {
                RaffleIcon(
                    iconId = milestone.iconId,
                    // Stepper line connecting milestone icons, per Figma: drawn in the icon's
                    // own coordinates (RTL-safe) with 4dp clearance; the overshoot past the
                    // cell is trimmed by MoonBundleCell's clip, so it never leaves the group.
                    modifier = Modifier.drawBehind {
                        val lineX = size.width / 2
                        val clearance = MILESTONE_CONNECTOR_CLEARANCE.toPx()
                        val overshoot = MILESTONE_CONNECTOR_OVERSHOOT.toPx()
                        val stroke = MILESTONE_CONNECTOR_STROKE.toPx()
                        if (position == MoonBundlePosition.Middle || position == MoonBundlePosition.Footer) {
                            drawLine(
                                color = connectorColor,
                                start = Offset(lineX, -overshoot),
                                end = Offset(lineX, -clearance),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round,
                            )
                        }
                        if (position == MoonBundlePosition.Middle || position == MoonBundlePosition.Header) {
                            drawLine(
                                color = connectorColor,
                                start = Offset(lineX, size.height + clearance),
                                end = Offset(lineX, size.height + overshoot),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round,
                            )
                        }
                    },
                    grayscale = !done && !isCurrent,
                )
            },
            content = when {
                done -> {
                    {
                        MoonItemIcon(
                            painter = painterResource(UIKitIcon.ic_done_bold_16),
                            size = 16.dp,
                            color = UIKit.colorScheme.accent.blue,
                        )
                    }
                }
                isCurrent -> {
                    {
                        MoonItemIcon(
                            painter = painterResource(UIKitIcon.ic_chevron_right_16),
                            size = 16.dp,
                            color = UIKit.colorScheme.icon.tertiary,
                        )
                    }
                }
                else -> null
            },
            onClick = if (isCurrent) {
                {
                    onClickMilestone()
                    onOpenDeeplink(MILESTONE_SWAP_DEEPLINK)
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun FaqCell(
    item: RaffleListItem.Faq,
    formatter: DateTimeFormatter,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    fun OffsetDateTime.formatted(): String =
        atZoneSameInstant(ZoneId.systemDefault()).format(formatter)

    val question = item.questionDate
        ?.let { stringResource(item.question, it.formatted()) }
        ?: stringResource(item.question)
    val answerTickets = item.answerTickets
    val answer = when {
        item.answerDate != null -> stringResource(item.answer, item.answerDate.formatted())
        answerTickets != null -> stringResource(
            item.answer,
            pluralStringResource(Plurals.raffle_tickets, answerTickets, answerTickets),
        )
        else -> stringResource(item.answer)
    }
    MoonBundleCell(position = item.pos, onClick = onToggle) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = question,
                    style = UIKit.typography.label1,
                    color = UIKit.colorScheme.text.primary,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                MoonItemIcon(
                    painter = painterResource(
                        if (expanded) { UIKitIcon.ic_chevron_up_16 } else { UIKitIcon.ic_chevron_down_16 },
                    ),
                    size = 16.dp,
                    color = UIKit.colorScheme.icon.tertiary,
                )
            }
            MoonExpandable(visible = expanded) {
                Text(
                    text = answer,
                    style = UIKit.typography.body2,
                    color = UIKit.colorScheme.text.secondary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun HistoryCell(
    entry: RaffleEntity.HistoryItem,
    position: MoonBundlePosition,
    formatter: DateTimeFormatter,
) {
    MoonBundleCell(position = position) {
        TextCell(
            title = entry.title,
            subtitle = entry.awardedAt.atZoneSameInstant(ZoneId.systemDefault()).format(formatter),
            modifier = Modifier.padding(vertical = RAFFLE_CELL_EXTRA_PADDING),
            minHeight = RAFFLE_CELL_MIN_HEIGHT,
            image = {
                RaffleIcon(entry.iconId, grayscale = true)
            },
            content = {
                Text(
                    text = pluralStringResource(
                        Plurals.raffle_tickets_reward,
                        entry.tickets,
                        entry.tickets,
                    ),
                    style = UIKit.typography.label2,
                    color = UIKit.colorScheme.text.primary,
                )
            },
        )
    }
}

@Composable
private fun CtaButton(
    cta: RaffleEntity.Cta,
    modifier: Modifier,
    onClickCta: () -> Unit,
    onOpenDeeplink: (String) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    MoonAccentButton(
        text = cta.title,
        size = ButtonSizeLarge,
        modifier = modifier,
        onClick = {
            onClickCta()
            when (cta.action) {
                RaffleEntity.Cta.Action.Deeplink -> onOpenDeeplink(cta.payload)
                RaffleEntity.Cta.Action.Link -> onOpenLink(cta.payload)
            }
        },
    )
}

@Preview
@Composable
private fun RaffleScreenActivePreview() {
    ThemedPreview(isDarkOnly = true) {
        RaffleScreenInternal(
            state = previewContent(),
            source = MysteryRaffleSource.WalletMain,
            onClose = {},
            onRefresh = {},
            onOpenDeeplink = {},
            onOpenLink = {},
        )
    }
}

@Preview
@Composable
private fun RaffleScreenLoadingPreview() {
    ThemedPreview(isDarkOnly = true) {
        RaffleScreenInternal(
            state = RaffleScreenState.Loading,
            source = MysteryRaffleSource.WalletMain,
            onClose = {},
            onRefresh = {},
            onOpenDeeplink = {},
            onOpenLink = {},
        )
    }
}

@Preview
@Composable
private fun RaffleScreenErrorPreview() {
    ThemedPreview(isDarkOnly = true) {
        RaffleScreenInternal(
            state = RaffleScreenState.Error,
            source = MysteryRaffleSource.DeepLink,
            onClose = {},
            onRefresh = {},
            onOpenDeeplink = {},
            onOpenLink = {},
        )
    }
}

@Preview
@Composable
private fun RaffleScreenOfferPreview() {
    ThemedPreview(isDarkOnly = true) {
        RaffleScreenInternal(
            state = previewOfferContent(),
            source = MysteryRaffleSource.DeepLink,
            onClose = {},
            onRefresh = {},
            onOpenDeeplink = {},
            onOpenLink = {},
        )
    }
}

@Preview
@Composable
private fun RaffleScreenWonPreview() {
    ThemedPreview(isDarkOnly = true) {
        RaffleScreenInternal(
            state = previewWonContent(),
            source = MysteryRaffleSource.DeepLink,
            onClose = {},
            onRefresh = {},
            onOpenDeeplink = {},
            onOpenLink = {},
        )
    }
}

private fun previewContent(): RaffleScreenState.Content {
    val task = RaffleEntity.Task(
        id = "swap",
        iconId = "swap",
        title = "Make $100+ in cross-chain swap volume",
        subtitle = "1 ticket per every $100 volume",
        rewardTickets = 1,
    )
    val milestone = RaffleEntity.Milestone(
        id = "keeper",
        iconId = "keeper",
        title = "Keeper · $1 000 volume",
        subtitle = "+10 bonus tickets",
        rewardTickets = 10,
        done = true,
    )
    val raffle = RaffleEntity(
        id = "preview",
        status = RaffleEntity.Status.Joined,
        hero = RaffleEntity.Hero(image = "", badgeIconId = "ticket"),
        title = "Win a share of $50K prize pool",
        subtitle = "Complete tasks, make cross-chain swaps, and earn tickets.",
        startsAt = OffsetDateTime.now().minusDays(7),
        endsAt = OffsetDateTime.now().plusDays(23),
        compactBanner = RaffleEntity.CompactBanner(
            defaultTitle = "Join Mystery Raffle",
            activeTitle = "Mystery Raffle",
            iconId = "ticket",
        ),
        prizesHeader = "More than 2 000 prizes",
        statusBadge = "Mystery Raffle",
        prizes = listOf(
            RaffleEntity.Prize(id = "pepe", image = "", title = "Plush Pepe NFT", subtitle = "for 1 winner"),
        ),
        tasks = listOf(task),
        milestones = listOf(milestone),
        cta = RaffleEntity.Cta(
            title = "Swap to Get Tickets",
            action = RaffleEntity.Cta.Action.Deeplink,
            payload = "tonkeeper://swap",
        ),
        progress = RaffleEntity.Progress(ticketsTotal = 123, history = emptyList()),
    )
    return RaffleScreenState.Content(
        raffle = raffle,
        kind = RaffleKind.Active,
        items = listOf(
            RaffleListItem.Hero,
            RaffleListItem.Countdown(raffle.endsAt),
            RaffleListItem.Tickets,
            RaffleListItem.BenefitHeader("More than 2 000 prizes"),
            RaffleListItem.PrizesRow,
            RaffleListItem.SectionHeader(Localization.raffle_how_to_earn),
            RaffleListItem.Task(task, MoonBundlePosition.Default),
            RaffleListItem.SectionHeader(Localization.raffle_milestones),
            RaffleListItem.Milestone(milestone, MoonBundlePosition.Default, isCurrent = true),
            RaffleListItem.SectionHeader(Localization.raffle_faq),
            RaffleListItem.Faq(
                id = "earn",
                question = Localization.raffle_faq_earn_q,
                answer = Localization.raffle_faq_earn_a,
                answerTickets = 10,
            ),
        ),
        earnHeaderIndex = 5,
        participating = true,
        showGetMore = true,
        analyticsKind = MysteryRaffleKind.Active,
        ctaAction = MysteryRaffleAction.Swap,
        ticketsTotal = 123,
        prize = null,
    )
}

private fun previewOfferContent(): RaffleScreenState.Content {
    val zeroFee = RaffleEntity.BenefitCard(
        id = "zero_fee",
        iconId = "zero_fee",
        label = "Available now",
        title = "Zero-fee cross-chain swaps",
        subtitle = "Ends in 6 days",
    )
    val tickets = RaffleEntity.BenefitCard(
        id = "tickets",
        iconId = "ticket",
        label = "Available now",
        title = "10 mystery tickets",
        subtitle = "You'll see why soon",
    )
    val raffle = RaffleEntity(
        id = "preview-offer",
        status = RaffleEntity.Status.NotJoined,
        hero = RaffleEntity.Hero(image = "", badgeIconId = "ticket"),
        title = "Join Mystery Raffle",
        subtitle = "Migrate assets from your TON wallet to multichain and unlock benefits.",
        startsAt = OffsetDateTime.now().minusDays(1),
        endsAt = OffsetDateTime.now().plusDays(23),
        compactBanner = RaffleEntity.CompactBanner(
            defaultTitle = "Join Mystery Raffle",
            activeTitle = "Mystery Raffle",
            iconId = "ticket",
        ),
        prizesHeader = "More than 2 000 prizes",
        statusBadge = "Early Access",
        prizes = emptyList(),
        tasks = emptyList(),
        milestones = emptyList(),
        benefitCards = listOf(zeroFee, tickets),
        cta = RaffleEntity.Cta(
            title = "Migrate to Unlock Benefits",
            action = RaffleEntity.Cta.Action.Deeplink,
            payload = "tonkeeper://migrate",
        ),
    )
    return RaffleScreenState.Content(
        raffle = raffle,
        kind = RaffleKind.Offer,
        items = listOf(
            RaffleListItem.Hero,
            RaffleListItem.RevealDate(OffsetDateTime.now().plusDays(24)),
            RaffleListItem.Benefit(zeroFee, first = true),
            RaffleListItem.Benefit(tickets, first = false),
            RaffleListItem.SectionHeader(Localization.raffle_faq),
            RaffleListItem.Faq(
                id = "about",
                question = Localization.raffle_faq_about_q,
                answer = Localization.raffle_faq_about_a,
                pos = MoonBundlePosition.Header,
                answerDate = OffsetDateTime.now().plusDays(6),
            ),
            RaffleListItem.Faq(
                id = "fee_end",
                question = Localization.raffle_faq_fee_end_q,
                answer = Localization.raffle_faq_fee_end_a,
                pos = MoonBundlePosition.Footer,
                answerDate = OffsetDateTime.now().plusDays(6),
            ),
        ),
        earnHeaderIndex = -1,
        participating = true,
        showGetMore = false,
        analyticsKind = MysteryRaffleKind.Migration,
        ctaAction = MysteryRaffleAction.Migrate,
        ticketsTotal = 0,
        prize = null,
    )
}

private fun previewWonContent(): RaffleScreenState.Content {
    val historyEntry = RaffleEntity.HistoryItem(
        id = "swap:1",
        awardedAt = OffsetDateTime.now().minusDays(3),
        iconId = "swap",
        title = "$100 swap",
        tickets = 1,
    )
    val raffle = RaffleEntity(
        id = "preview-won",
        status = RaffleEntity.Status.Won,
        hero = RaffleEntity.Hero(image = "", badgeIconId = "ticket"),
        title = "You won a Plush Pepe NFT",
        subtitle = "Congratulations! Your prize will be added to your wallet within 7 days.",
        startsAt = OffsetDateTime.now().minusDays(30),
        endsAt = OffsetDateTime.now().minusDays(1),
        compactBanner = RaffleEntity.CompactBanner(
            defaultTitle = "Join Mystery Raffle",
            activeTitle = "Mystery Raffle",
            iconId = "ticket",
        ),
        prizesHeader = "More than 2 000 prizes",
        statusBadge = "You won",
        prizes = emptyList(),
        tasks = emptyList(),
        milestones = emptyList(),
        cta = RaffleEntity.Cta(
            title = "See All Winners on Telegram",
            action = RaffleEntity.Cta.Action.Link,
            payload = "https://t.me/tonkeeper",
        ),
        progress = RaffleEntity.Progress(
            ticketsTotal = 123,
            history = listOf(historyEntry),
            winningPrize = RaffleEntity.WinningPrize(
                title = "Plush Pepe NFT",
                image = "",
            ),
        ),
    )
    return RaffleScreenState.Content(
        raffle = raffle,
        kind = RaffleKind.Result,
        items = listOf(
            RaffleListItem.Hero,
            RaffleListItem.Tickets,
            RaffleListItem.SectionHeader(Localization.raffle_history),
            RaffleListItem.History(historyEntry, MoonBundlePosition.Default),
        ),
        earnHeaderIndex = -1,
        participating = false,
        showGetMore = false,
        analyticsKind = MysteryRaffleKind.Won,
        ctaAction = MysteryRaffleAction.ResultsLink,
        ticketsTotal = 123,
        prize = "Plush Pepe NFT",
    )
}
