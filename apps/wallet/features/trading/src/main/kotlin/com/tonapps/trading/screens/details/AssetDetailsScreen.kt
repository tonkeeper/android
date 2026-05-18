package com.tonapps.trading.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.bus.generated.Events.AssetScreen.AssetScreenButton
import com.tonapps.mvi.props.observeSafeState
import com.tonapps.trading.asTokenEntity
import com.tonapps.trading.assetChainImageUrl
import com.tonapps.trading.omnistonBuyTokens
import com.tonapps.trading.omnistonSellTokens
import com.tonapps.trading.percentDiffColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.events.tx.model.TxEvent
import com.tonapps.wallet.localization.Localization
import io.tradingapi.models.AssetRef.Verification
import io.tradingapi.models.AssetType
import ui.components.events.EventItem
import ui.components.events.EventItemClickPart
import ui.components.moon.MoonActionIcon
import ui.components.moon.MoonDivider
import ui.components.moon.MoonExpandableText
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonTag
import ui.components.moon.MoonTags
import ui.components.moon.MoonTextShimmer
import ui.components.moon.MoonTopAppBar
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.TextCell
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.components.moon.container.MoonScaffold
import ui.components.moon.screen.MoonEmptyScreen
import ui.components.popup.ActionMenu
import ui.components.popup.ActionMenuItem
import ui.components.popup.MoonIconTooltip
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.UIKit
import ui.utils.toRichSpanStyle
import uikit.chart.ChartPeriod
import uikit.chart.ChartPoint

@Composable
fun AssetDetailsScreen(
    feature: AssetDetailsFeature,
    previewName: String,
    previewImageUrl: String,
    onOpenToken: (token: TokenEntity, eventsOnly: Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenSwap: (fromToken: String, toToken: String) -> Unit,
    onOpenSend: (tokenAddress: String) -> Unit,
    onOpenReceive: (token: TokenEntity?) -> Unit,
    onOpenTxDetails: (tx: TxEvent, actionIndex: Int) -> Unit,
    onOpenStaking: () -> Unit,
    onOpenUnverifiedInfo: () -> Unit,
    onBack: () -> Unit,
) {
    val state by feature.state.global.observeSafeState()

    LaunchedEffect(feature) {
        feature.events.collect { event ->
            when (event) {
                is AssetDetailsEvent.OpenUrl -> onOpenUrl(event.url)
            }
        }
    }

    AssetDetailsContent(
        state = state,
        previewName = previewName,
        previewImageUrl = previewImageUrl,
        onChartPeriodChange = { feature.sendAction(AssetDetailsAction.SetChartPeriod(it)) },
        onOpenToken = onOpenToken,
        onOpenUrl = onOpenUrl,
        onOpenSwap = onOpenSwap,
        onOpenSend = onOpenSend,
        onOpenReceive = onOpenReceive,
        onOpenTxDetails = onOpenTxDetails,
        onOpenExplorer = { feature.sendAction(AssetDetailsAction.OpenTokenExplorer) },
        onOpenStaking = onOpenStaking,
        onOpenUnverifiedInfo = onOpenUnverifiedInfo,
        onTrackButtonClick = { feature.trackButtonClick(it) },
        onBack = onBack,
    )
}

@Composable
private fun AssetDetailsContent(
    state: AssetDetailsState,
    previewName: String,
    previewImageUrl: String,
    onChartPeriodChange: (ChartPeriod) -> Unit,
    onOpenToken: (token: TokenEntity, eventsOnly: Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenSwap: (fromToken: String, toToken: String) -> Unit,
    onOpenSend: (tokenAddress: String) -> Unit,
    onOpenReceive: (token: TokenEntity?) -> Unit,
    onOpenTxDetails: (tx: TxEvent, actionIndex: Int) -> Unit,
    onOpenExplorer: () -> Unit,
    onOpenStaking: () -> Unit,
    onOpenUnverifiedInfo: () -> Unit,
    onTrackButtonClick: (AssetScreenButton) -> Unit = {},
    onBack: () -> Unit,
) {
    val data = state as? AssetDetailsState.Data
    val asset = data?.details?.asset
    val title = asset?.name ?: previewName
    val isUnverified = asset != null && asset.verification != Verification.whitelist
    val subtitle = when {
        asset?.assetType == AssetType.stocks -> stringResource(Localization.tokenized_stocks)
        asset?.assetType == AssetType.etfs -> stringResource(Localization.tokenized_etfs)
        isUnverified -> stringResource(Localization.unverified_token)
        else -> null
    }
    val imageUrl = previewImageUrl.ifBlank { asset?.imageUrl } ?: ""
    val chainImageUrl = when (asset?.assetType) {
        AssetType.stocks,
        AssetType.etfs -> null
        else -> asset?.id?.assetChainImageUrl()
    }

    val scrollState = rememberScrollState()
    val showDivider by remember {
        derivedStateOf { scrollState.value > 0 }
    }
    val showBottomBarDivider by remember {
        derivedStateOf { scrollState.value < scrollState.maxValue }
    }

    var infoModalType by remember { mutableStateOf<AssetInfoModalType?>(null) }
    var topActionMenuExpanded by remember { mutableStateOf(false) }

    val onTitleClick = {
        if (isUnverified) {
            onOpenUnverifiedInfo()
        } else {
            infoModalType = when(asset?.assetType) {
                AssetType.stocks -> AssetInfoModalType.Stock
                AssetType.etfs -> AssetInfoModalType.Etf
                else -> null
            }
        }
    }

    val subtitleColor = if (isUnverified) {
        UIKit.colorScheme.accent.orange
    } else {
        UIKit.colorScheme.text.accent
    }

    MoonScaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(rememberNestedScrollInteropConnection()),
        topBar = {
            MoonTopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = title,
                onTitleClick = onTitleClick,
                subtitleContent = subtitle?.let {
                    {
                        Row(
                            modifier = Modifier.fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = subtitle,
                                color = subtitleColor,
                                style = UIKit.typography.body2,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            MoonItemIcon(
                                painter = painterResource(UIKitIcon.ic_information_circle_12),
                                color = subtitleColor,
                            )
                        }
                    }
                },
                navigationIconRes = UIKitIcon.ic_chevron_left_16,
                onNavigationClick = onBack,
                hasCustomActions = state is AssetDetailsState.Data,
                actions = {
                    if (state is AssetDetailsState.Data) {
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
                            ) {
                                ActionMenuItem(
                                    text = stringResource(Localization.view_details),
                                    painter = painterResource(UIKitIcon.ic_globe_16),
                                    onClick = {
                                        topActionMenuExpanded = false
                                        onOpenExplorer()
                                    }
                                )
                            }
                        }
                    }
                },
                showDivider = showDivider,
            )
        },
    ) {
        if (state is AssetDetailsState.Error) {
            MoonEmptyScreen(
                modifier = Modifier.fillMaxSize(),
                text = stringResource(Localization.cant_find_anything),
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                AssetDetailsData(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    sections = data?.sections,
                    imageUrl = imageUrl,
                    chainImageUrl = chainImageUrl,
                    currencyCode = data?.currencyCode ?: "",
                    chartData = data?.chartData ?: emptyList(),
                    chartPeriod = data?.chartPeriod ?: ChartPeriod.month,
                    maxStakingApyFormatted = data?.maxStakingApyFormatted,
                    onOpenToken = onOpenToken,
                    onOpenUrl = onOpenUrl,
                    onChartPeriodChange = onChartPeriodChange,
                    onOpenTxDetails = onOpenTxDetails,
                    onOpenStaking = onOpenStaking,
                    showChart = true,
                )
                if (showBottomBarDivider) {
                    MoonDivider()
                }
                AssetDetailsBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    hasBalance = data?.sections?.balance != null,
                    onBuyClick = {
                        val current = data ?: return@AssetDetailsBottomBar
                        onTrackButtonClick(AssetScreenButton.Buy)
                        val (from, to) = current.details.asset.omnistonBuyTokens()
                        onOpenSwap(from, to)
                    },
                    onSellClick = {
                        val current = data ?: return@AssetDetailsBottomBar
                        onTrackButtonClick(AssetScreenButton.Sell)
                        val (from, to) = current.details.asset.omnistonSellTokens()
                        onOpenSwap(from, to)
                    },
                    onSendClick = {
                        val current = data ?: return@AssetDetailsBottomBar
                        onTrackButtonClick(AssetScreenButton.Send)
                        onOpenSend(current.details.asset.asTokenEntity.address)
                    },
                    onReceiveClick = {
                        val current = data ?: return@AssetDetailsBottomBar
                        onTrackButtonClick(AssetScreenButton.Receive)
                        onOpenReceive(current.details.asset.asTokenEntity)
                    },
                )
            }
        }
    }

    infoModalType?.let { modalType ->
        AssetDetailsInfoModal(
            type = modalType,
            onDismiss = { infoModalType = null },
        )
    }
}

@Composable
private fun AssetDetailsData(
    modifier: Modifier = Modifier,
    sections: AssetDetailsSections?,
    imageUrl: String,
    chainImageUrl: String?,
    currencyCode: String,
    chartData: List<ChartPoint>,
    chartPeriod: ChartPeriod,
    maxStakingApyFormatted: String? = null,
    onOpenToken: (token: TokenEntity, eventsOnly: Boolean) -> Unit,
    onOpenUrl: (String) -> Unit,
    onChartPeriodChange: (ChartPeriod) -> Unit,
    onOpenTxDetails: (tx: TxEvent, actionIndex: Int) -> Unit,
    onOpenStaking: () -> Unit,
    showChart: Boolean = true,
) {
    Column(modifier = modifier) {
        if (showChart) {
            ChartSection(
                imageUrl = imageUrl,
                chainImageUrl = chainImageUrl,
                maxStakingApyFormatted = maxStakingApyFormatted,
                currencyCode = currencyCode,
                chartData = chartData,
                chartPeriod = chartPeriod,
                onPeriodChange = onChartPeriodChange,
                onOpenStaking = onOpenStaking,
            )
            MoonDivider()
        }
        if (sections == null) {
            DetailsBalanceSectionShimmer()
        } else {
            sections.balance?.let {
                DetailsBalanceSection(
                    balance = it,
                    imageUrl = imageUrl,
                    chainImageUrl = chainImageUrl,
                )
            }
        }
        sections?.recentEvents?.let {
            EventsSection(
                recentEvents = it,
                onOpenToken = onOpenToken,
                onOpenTxDetails = onOpenTxDetails,
            )
        }
        sections?.about?.let { DetailsAboutSection(about = it) }
        sections?.overview?.let { DetailsOverviewSection(overview = it) }
        sections?.trading?.let { DetailsTradingSection(trading = it) }
        sections?.links?.let { DetailsLinksSection(links = it, onOpenUrl = onOpenUrl) }
    }
}

@Composable
private fun DetailsBalanceSection(
    balance: AssetDetailsSections.Balance,
    imageUrl: String,
    chainImageUrl: String?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(text = stringResource(Localization.your_balance))
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
                            size = 44.dp,
                        )
                    }
                },
                minHeight = 76.dp,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DetailsBalanceSectionShimmer() {
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
private fun EventsSection(
    recentEvents: AssetDetailsSections.RecentEvents,
    onOpenToken: (token: TokenEntity, eventsOnly: Boolean) -> Unit,
    onOpenTxDetails: (tx: TxEvent, actionIndex: Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(
            text = stringResource(Localization.transaction_history),
            onSeeAllClick = if (recentEvents.showSeeAll) {
                { onOpenToken(recentEvents.token, true) }
            } else {
                null
            }
        )
        MoonBundleCell {
            Column {
                recentEvents.items.forEachIndexed { index, row ->
                    if (index > 0) MoonDivider()
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
private fun DetailsAboutSection(
    about: AssetDetailsSections.About,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(text = stringResource(Localization.about))
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
private fun DetailsOverviewSection(
    overview: AssetDetailsSections.Overview,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(text = stringResource(Localization.overview))
        MoonBundleCell {
            Column {
                overview.items.forEachIndexed { index, item ->
                    if (index > 0) MoonDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.wrapContentWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(item.titleRes),
                                style = UIKit.typography.body1,
                                color = UIKit.colorScheme.text.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            MoonIconTooltip(text = stringResource(item.tooltipTextRes))
                        }
                        Text(
                            text = item.valueFormatted,
                            style = UIKit.typography.label1,
                            color = UIKit.colorScheme.text.primary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DetailsTradingSection(
    trading: AssetDetailsSections.Trading,
) {
    val percentStyle = UIKit.typography.body1
    val volumeChangeFormatted = trading.volumeChange24hFormatted
    val percentSpanColor = volumeChangeFormatted?.percentDiffColor()

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(text = stringResource(Localization.trading_activity))
        MoonBundleCell {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(Localization.volume),
                            style = UIKit.typography.body1,
                            color = UIKit.colorScheme.text.secondary
                        )
                        MoonIconTooltip(text = stringResource(Localization.volume_tooltip))
                    }
                    Text(
                        text = buildAnnotatedString {
                            append(trading.volume24hFormatted)
                            if (volumeChangeFormatted != null && percentSpanColor != null) {
                                append(" ")
                                withStyle(percentStyle.toRichSpanStyle(color = percentSpanColor)) {
                                    append(volumeChangeFormatted)
                                }
                            }
                        },
                        style = UIKit.typography.label1,
                        color = UIKit.colorScheme.text.primary
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
            style = UIKit.typography.body3,
            color = UIKit.colorScheme.text.tertiary,
            text = stringResource(Localization.trading_activity_note),
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DetailsLinksSection(
    links: AssetDetailsSections.Links,
    onOpenUrl: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitle(text = stringResource(Localization.links))
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

private val previewChartData = listOf(
    ChartPoint(date = 1_700_000_000L, price = 2.10f),
    ChartPoint(date = 1_700_086_400L, price = 2.35f),
    ChartPoint(date = 1_700_172_800L, price = 2.20f),
    ChartPoint(date = 1_700_259_200L, price = 2.55f),
    ChartPoint(date = 1_700_345_600L, price = 2.80f),
    ChartPoint(date = 1_700_432_000L, price = 2.65f),
    ChartPoint(date = 1_700_518_400L, price = 3.05f),
)

private val previewAssetDetailsSections = AssetDetailsSections(
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
    ),
    recentEvents = null,
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

@Preview
@Composable
private fun AssetDetailsScreenPreview() {
    ThemedPreview {
        MoonScaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(rememberNestedScrollInteropConnection())
                .statusBarsPadding(),
            title = "Toncoin",
            onBack = {},
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                AssetDetailsData(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    sections = previewAssetDetailsSections,
                    imageUrl = "",
                    chainImageUrl = null,
                    currencyCode = "USD",
                    chartData = previewChartData,
                    chartPeriod = ChartPeriod.month,
                    onOpenToken = { _, _ -> },
                    onOpenUrl = {},
                    onChartPeriodChange = {},
                    onOpenTxDetails = { _, _ -> },
                    onOpenStaking = {},
                    showChart = true,
                )
                AssetDetailsBottomBar(
                    modifier = Modifier.navigationBarsPadding(),
                    hasBalance = true,
                )
            }
        }
    }
}
