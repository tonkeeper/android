package com.tonapps.trading.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.trading.percentDiffColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.events.tx.model.TxEvent
import com.tonapps.wallet.features.events.components.ActivityEventCell
import com.tonapps.wallet.features.events.data.HistoryEventEntity
import com.tonapps.wallet.features.events.screens.details.EventDetailsModal
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.features.events.components.legacy.EventItem
import com.tonapps.wallet.features.events.components.legacy.EventItemClickPart
import ui.components.moon.MoonActionIcon
import ui.components.moon.MoonCircleIcon
import ui.components.moon.MoonDivider
import ui.components.moon.MoonExpandableText
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonLabel
import ui.components.moon.MoonLabelDefault
import ui.components.moon.MoonTag
import ui.components.moon.MoonTags
import ui.components.moon.MoonTextShimmer
import ui.components.moon.MoonTopAppBar
import ui.components.moon.MoonTopAppBarSubtitle
import ui.components.moon.MoonTopAppBarTitle
import ui.components.moon.MoonVerificationBadge
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundlePosition
import ui.components.moon.cell.MoonBundleTitleCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.cell.MoonPropertyTitle
import ui.components.moon.cell.MoonPropertyValue
import ui.components.moon.cell.TextCell
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.components.popup.ActionMenu
import ui.components.popup.ComposeActionItem
import ui.painterResource
import ui.theme.UIKit
import ui.utils.toRichSpanStyle
import uikit.chart.ChartPoint

private object AssetDetailsMenuAction {
    const val VIEW_DETAILS = "view_details"
    const val TOGGLE_VISIBILITY = "toggle_visibility"
}

@Composable
internal fun AssetDetailsTopBar(
    state: AssetDetailsState,
    title: String,
    subtitle: String?,
    showDivider: Boolean,
    onOpenExplorer: () -> Unit,
    onToggleVisibility: () -> Unit,
    onToggleFavorite: () -> Unit,
    onBack: () -> Unit,
    subtitleColor: Color = UIKit.colorScheme.text.secondary,
    showVerifiedBadge: Boolean = false,
    onVerifiedBadgeClick: () -> Unit = {},
) {
    var topActionMenuExpanded by remember { mutableStateOf(false) }

    MoonTopAppBar(
        modifier = Modifier.statusBarsPadding(),
        title = {
            if (showVerifiedBadge) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MoonTopAppBarTitle(
                        text = title,
                        modifier = Modifier.weight(1f, fill = false),
                        autoShrink = true,
                    )
                    MoonVerificationBadge()
                }
            } else {
                MoonTopAppBarTitle(text = title, autoShrink = true)
            }
        },
        onTitleClick = if (showVerifiedBadge) {
            { onVerifiedBadgeClick() }
        } else {
            null
        },
        subtitle = subtitle?.takeIf { it.isNotBlank() }?.let { text ->
            { MoonTopAppBarSubtitle(text = text, color = subtitleColor) }
        },
        navigationIconRes = UIKitIcon.ic_chevron_left_16,
        onNavigationClick = onBack,
        hasCustomActions = state is AssetDetailsState.Data,
        actions = {
            if (state is AssetDetailsState.Data) {
                val isHidden = state.isHidden
                val isFavorite = state.isFavorite
                val menuItems = buildList {
                    add(
                        ComposeActionItem(
                            id = AssetDetailsMenuAction.VIEW_DETAILS,
                            text = stringResource(Localization.view_details),
                            iconPainter = painterResource(UIKitIcon.ic_globe_16),
                        )
                    )
                    if (isHidden != null) {
                        add(
                            ComposeActionItem(
                                id = AssetDetailsMenuAction.TOGGLE_VISIBILITY,
                                text = if (isHidden) {
                                    stringResource(Localization.show_in_portfolio)
                                } else {
                                    stringResource(Localization.hide_from_portfolio)
                                },
                                iconPainter = painterResource(
                                    if (isHidden) {
                                        UIKitIcon.ic_eye_outline_28
                                    } else {
                                        UIKitIcon.ic_eye_disable_16
                                    }
                                ),
                            )
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        MoonActionIcon(
                            painter = painterResource(UIKitIcon.ic_ellipsis_16),
                            onClick = { topActionMenuExpanded = true },
                            tintColor = UIKit.colorScheme.buttonSecondary.primaryForeground,
                            backgroundColor = UIKit.colorScheme.buttonSecondary.primaryBackground,
                        )
                        ActionMenu(
                            expanded = topActionMenuExpanded,
                            onDismissRequest = { topActionMenuExpanded = false },
                            items = menuItems,
                            onItemClick = { item, _ ->
                                topActionMenuExpanded = false
                                when (item.id) {
                                    AssetDetailsMenuAction.VIEW_DETAILS -> onOpenExplorer()
                                    AssetDetailsMenuAction.TOGGLE_VISIBILITY -> onToggleVisibility()
                                }
                            },
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FavoriteActionIcon(
                        isFavorite = isFavorite,
                        onClick = onToggleFavorite,
                    )
                }
            }
        },
        showDivider = showDivider,
    )
}

@Composable
internal fun DisclaimerBanner(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(color.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = color,
            style = UIKit.typography.body2,
        )

        Spacer(modifier = Modifier.width(4.dp))

        MoonItemIcon(
            painter = painterResource(UIKitIcon.ic_chevron_right_12),
            color = color,
        )
    }
}

@Composable
internal fun DetailsBalanceSection(
    balance: AssetDetailsSections.Balance,
    imageUrl: String,
    chainImageUrl: String?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MoonBundleTitleCell(
            title = stringResource(Localization.your_balance)
        )

        MoonBundleCell {
            TextCell(
                title = balance.balanceFormatted,
                subtitle = balance.fiatFormatted,
                image = {
                    MoonCutBadgedBox(
                        badge = {
                            chainImageUrl?.let {
                                MoonItemImage(
                                    image = chainImageUrl,
                                    size = 20.dp
                                )
                            }
                        },
                        direction = BadgeDirection.EndBottom,
                    ) {
                        MoonItemImage(
                            image = imageUrl,
                            placeholder = painterResource(UIKitIcon.ic_illustration),
                            size = 44.dp,
                        )
                    }
                },
                minHeight = 76.dp,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
internal fun PerpsSection() {
    Column {
        MoonBundleTitleCell(
            title = stringResource(Localization.perp_position),
        )

        MoonBundleCell {
            TextCell(
                title = stringResource(Localization.trade_ton_perps),
                subtitle = stringResource(Localization.trade_ton_perps_subtitle),
                tags = {
                    MoonLabel(
                        text = stringResource(Localization.badge_new),
                        colors = MoonLabelDefault.blue(),
                    )
                },
                image = {
                    MoonCircleIcon(
                        painter = androidx.compose.ui.res.painterResource(UIKitIcon.ic_perps_28),
                        color = UIKit.colorScheme.accent.blue.copy(alpha = 0.12f),
                        size = 44.dp
                    )
                },
                content = {
                    MoonItemIcon(painterResource(UIKitIcon.ic_chevron_right_16))
                },
                minHeight = 76.dp,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
internal fun DetailsBalanceSectionShimmer() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MoonTextShimmer(
                text = stringResource(Localization.your_balance),
                style = UIKit.typography.label1,
                cornerRadius = 12.dp,
            )
        }
        MoonBundleCell {
            TextCell(
                title = {
                    MoonTextShimmer(
                        text = "0,000.00 TON",
                        style = UIKit.typography.label1,
                        cornerRadius = 8.dp,
                        backgroundFill = UIKit.colorScheme.background.contentTint,
                    )
                },
                subtitle = {
                    MoonTextShimmer(
                        text = "0,000.00 $",
                        style = UIKit.typography.body2,
                        cornerRadius = 8.dp,
                        backgroundFill = UIKit.colorScheme.background.contentTint,
                    )
                },
                image = {
                    Box(
                        modifier = Modifier
                            .requiredSize(44.dp)
                            .background(
                                color = UIKit.colorScheme.background.contentTint,
                                shape = CircleShape,
                            ),
                    )
                },
                minHeight = 76.dp,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
internal fun EventsSection(
    recentEvents: AssetDetailsSections.RecentEvents,
    onOpenToken: (token: TokenEntity, eventsOnly: Boolean) -> Unit,
    onOpenTxDetails: (tx: TxEvent, actionIndex: Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MoonBundleTitleCell(
            title = stringResource(Localization.transaction_history),
            content = if (recentEvents.showSeeAll) {
                {
                    Text(
                        text = stringResource(Localization.see_all),
                        modifier = Modifier.clickable {
                            onOpenToken(recentEvents.token, true)
                        },
                        style = UIKit.typography.body2,
                        color = UIKit.colorScheme.text.accent,
                    )
                }
            } else {
                null
            },
        )
        MoonBundleCell {
            Column {
                recentEvents.items.forEachIndexed { index, row ->
                    if (index > 0) {
                        MoonDivider()
                    }
                    EventItem(
                        event = row.uiEvent,
                        hiddenBalances = recentEvents.hiddenBalances,
                        onClick = { _, part ->
                            if (part is EventItemClickPart.Action) {
                                onOpenTxDetails(row.txEvent, part.index)
                            }
                        },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
internal fun ActivitiesSection(
    recentActivities: AssetDetailsSections.RecentActivities,
    onSeeAllClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MoonBundleTitleCell(
            title = stringResource(Localization.transaction_history),
            content = {
                Text(
                    text = stringResource(Localization.see_all),
                    modifier = Modifier.clickable(onClick = onSeeAllClick),
                    style = UIKit.typography.body2,
                    color = UIKit.colorScheme.text.accent,
                )
            },
        )
        var selectedActivity by remember { mutableStateOf<HistoryEventEntity?>(null) }
        recentActivities.items.forEachIndexed { index, activity ->
            ActivityEventCell(
                activity = activity,
                position = MoonBundlePosition.default(
                    size = recentActivities.items.size,
                    index = index,
                ),
                onClick = { selectedActivity = activity },
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        selectedActivity?.let { activity ->
            EventDetailsModal(
                event = activity,
                wallet = recentActivities.wallet,
                onDismiss = { selectedActivity = null },
            )
        }
    }
}

@Composable
internal fun DetailsAboutSection(
    about: AssetDetailsSections.About,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MoonBundleTitleCell(title = stringResource(Localization.about))
        MoonBundleCell {
            MoonExpandableText(
                modifier = Modifier.padding(16.dp),
                text = about.body,
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.text.primary,
                maxLines = 3,
                showMoreText = stringResource(Localization.more),
                showMoreColor = UIKit.colorScheme.text.accent,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
internal fun DetailsOverviewSection(
    overview: AssetDetailsSections.Overview,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MoonBundleTitleCell(title = stringResource(Localization.overview))
        MoonBundleCell {
            Column {
                overview.items.forEachIndexed { index, item ->
                    if (index > 0) {
                        MoonDivider()
                    }
                    MoonPropertyBigCell(
                        title = {
                            MoonPropertyTitle(
                                title = stringResource(item.titleRes),
                                infoTooltip = stringResource(item.tooltipTextRes),
                            )
                        },
                        content = {
                            MoonPropertyValue(title = item.valueFormatted)
                        },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
internal fun DetailsTradingSection(
    trading: AssetDetailsSections.Trading,
    onOpenUrl: (String) -> Unit,
) {
    val percentStyle = UIKit.typography.body1
    val volumeChangeFormatted = trading.volumeChange24hFormatted
    val percentSpanColor = volumeChangeFormatted?.percentDiffColor()

    val noteStyle = UIKit.typography.body3
    val linkColor = UIKit.colorScheme.text.accent
    val linkStyle = noteStyle.toRichSpanStyle(color = linkColor)
    val noteTemplate = stringResource(Localization.trading_activity_note)
    val note = remember(noteTemplate, trading, linkColor, onOpenUrl) {
        buildAttributionNote(
            template = noteTemplate,
            trading = trading,
            linkStyle = linkStyle,
            onOpenUrl = onOpenUrl,
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        MoonBundleTitleCell(title = stringResource(Localization.trading_activity))
        MoonBundleCell {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MoonPropertyTitle(
                        title = stringResource(Localization.volume),
                        infoTooltip = stringResource(Localization.volume_tooltip),
                    )
                    MoonPropertyValue(
                        title = buildAnnotatedString {
                            append(trading.volume24hFormatted)
                            if (volumeChangeFormatted != null && percentSpanColor != null) {
                                append(" ")
                                withStyle(percentStyle.toRichSpanStyle(color = percentSpanColor)) {
                                    append(volumeChangeFormatted)
                                }
                            }
                        },
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (trading.buyWeight > 0f) {
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .weight(trading.buyWeight)
                                .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                                .background(UIKit.colorScheme.accent.green)
                        )
                    }
                    if (trading.sellWeight > 0f) {
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .weight(trading.sellWeight)
                                .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                                .background(UIKit.colorScheme.accent.red)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            Localization.trading_activity_buy,
                            trading.buy24hFormatted
                        ),
                        style = UIKit.typography.body2,
                        color = UIKit.colorScheme.accent.green
                    )
                    Text(
                        text = stringResource(
                            Localization.trading_activity_sell,
                            trading.sell24hFormatted
                        ),
                        style = UIKit.typography.body2,
                        color = UIKit.colorScheme.accent.red
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            modifier = Modifier.padding(horizontal = 16.dp),
            style = noteStyle,
            color = UIKit.colorScheme.text.tertiary,
            text = note,
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun buildAttributionNote(
    template: String,
    trading: AssetDetailsSections.Trading,
    linkStyle: SpanStyle,
    onOpenUrl: (String) -> Unit,
): AnnotatedString {
    val placeholder = "%s"
    val placeholderIndex = template.indexOf(placeholder)
    return buildAnnotatedString {
        if (placeholderIndex >= 0) {
            append(template.substring(0, placeholderIndex))
            val sourceUrl = trading.sourceUrl
            if (sourceUrl != null) {
                withLink(
                    LinkAnnotation.Url(
                        url = sourceUrl,
                        styles = TextLinkStyles(linkStyle),
                        linkInteractionListener = { onOpenUrl(sourceUrl) },
                    )
                ) { append(trading.sourceName) }
            } else {
                append(trading.sourceName)
            }
            append(template.substring(placeholderIndex + placeholder.length))
        } else {
            append(template)
        }
    }
}

@Composable
internal fun DetailsLinksSection(
    links: AssetDetailsSections.Links,
    onOpenUrl: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MoonBundleTitleCell(title = stringResource(Localization.links))
        MoonTags(modifier = Modifier.padding(horizontal = 16.dp)) {
            links.items.forEach { item ->
                MoonTag(
                    text = item.name,
                    icon = painterResource(item.iconRes),
                    onClick = { onOpenUrl(item.url) },
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

internal val previewChartData = listOf(
    ChartPoint(date = 1_700_000_000L, price = 2.10f),
    ChartPoint(date = 1_700_086_400L, price = 2.35f),
    ChartPoint(date = 1_700_172_800L, price = 2.20f),
    ChartPoint(date = 1_700_259_200L, price = 2.55f),
    ChartPoint(date = 1_700_345_600L, price = 2.80f),
    ChartPoint(date = 1_700_432_000L, price = 2.65f),
    ChartPoint(date = 1_700_518_400L, price = 3.05f),
)

internal val previewAssetDetailsSections = AssetDetailsSections(
    balance = AssetDetailsSections.Balance(
        token = TokenEntity.TON,
        balanceFormatted = "123.45 TON",
        fiatFormatted = "$456.78",
    ),
    about = AssetDetailsSections.About(
        body = "Toncoin is the native cryptocurrency of TON blockchain.",
    ),
    overview = AssetDetailsSections.Overview(
        items = listOf(
            AssetDetailsSections.OverviewItem(
                titleRes = Localization.market_cap,
                tooltipTextRes = Localization.market_cap_tooltip,
                valueFormatted = "$1.2B",
            ),
            AssetDetailsSections.OverviewItem(
                titleRes = Localization.total_supply,
                tooltipTextRes = Localization.total_supply_tooltip,
                valueFormatted = "5.1B TON",
            ),
            AssetDetailsSections.OverviewItem(
                titleRes = Localization.circulating_supply,
                tooltipTextRes = Localization.circulating_supply_tooltip,
                valueFormatted = "4.8B TON",
            ),
        ),
    ),
    trading = AssetDetailsSections.Trading(
        volume24hFormatted = "$5.4M",
        volumeChange24hFormatted = "+3.21%",
        buyWeight = 0.55f,
        sellWeight = 0.45f,
        buy24hFormatted = "$3.0M",
        sell24hFormatted = "$2.4M",
        sourceName = "dyor.io",
        sourceUrl = "https://dyor.io/",
    ),
    recentEvents = null,
    recentActivities = null,
    links = AssetDetailsSections.Links(
        items = listOf(
            AssetDetailsSections.LinkItem(
                name = "Website",
                iconRes = UIKitIcon.ic_globe_16,
                url = "https://ton.org",
            ),
            AssetDetailsSections.LinkItem(
                name = "Telegram",
                iconRes = UIKitIcon.ic_telegram_16,
                url = "https://t.me/toncoin",
            ),
        ),
    ),
)
