package com.tonapps.perps.screens.details

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tonapps.paging.collectAsStateWorkaround
import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.data.PerpsAutoCloseLeg
import com.tonapps.perps.data.PerpsAutoCloseDraft
import com.tonapps.perps.data.PerpsAutoCloseLegKind
import com.tonapps.perps.data.PerpsCandle
import com.tonapps.perps.data.PerpsChartChange
import com.tonapps.perps.data.PerpsChartMode
import com.tonapps.perps.data.PerpsChartSectionState
import com.tonapps.perps.data.PerpsChartTimeframe
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLimitOrder
import com.tonapps.perps.data.PerpsMarketRules
import com.tonapps.perps.data.PerpsOpenPosition
import com.tonapps.perps.data.PerpsOperationPill
import com.tonapps.perps.data.PerpsOperationPillState
import com.tonapps.perps.data.PerpsPositionDetail
import com.tonapps.perps.data.PerpsPositionLevels
import com.tonapps.perps.data.PerpsPositionSection
import com.tonapps.perps.data.PerpsOrderSide
import com.tonapps.perps.data.PerpsPositionSide
import com.tonapps.perps.data.PerpsSizeDirection
import com.tonapps.perps.data.PerpsTradingFlags
import com.tonapps.perps.data.autoCloseLeverage
import com.tonapps.perps.data.formatCompactUsd
import com.tonapps.perps.data.formatFundingPercent
import com.tonapps.perps.data.formatSignedPercent
import com.tonapps.perps.data.formatSignedUsd
import com.tonapps.perps.data.formatUsd
import com.tonapps.perps.data.formatUsdPrice
import com.tonapps.perps.data.withLegs
import com.tonapps.perps.data.legsDraft
import com.tonapps.perps.data.perpsChartZoneOffsetSeconds
import com.tonapps.perps.data.priceChange24hAbsolute
import com.tonapps.perps.data.toOrderSide
import com.tonapps.perps.screens.common.openPerpsBasics
import com.tonapps.perps.screens.markets.PerpsAssetIcon
import com.tonapps.perps.screens.order.PerpsAutoCloseBounds
import com.tonapps.perps.screens.order.PerpsAutoCloseDialog
import com.tonapps.perps.screens.order.autoCloseEstimate
import com.tonapps.perps.screens.markets.perpsChangeColor
import com.tonapps.perps.screens.markets.perpsErrorDescription
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.data.multichain.perps.PerpsOperationKind
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import ui.components.moon.ButtonColorsPrimary
import ui.components.moon.ButtonColorsTertiary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonBottomBar
import ui.components.moon.MoonDivider
import ui.components.moon.MoonBadgeButton
import ui.components.moon.MoonExpandableText
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonLoader
import ui.components.moon.MoonTopAppBar
import ui.components.moon.MoonTopAppBarSubtitle
import ui.components.moon.MoonTopAppBarTitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundleTitleCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.cell.MoonPropertyTitle
import ui.components.moon.cell.MoonPropertyValue
import ui.components.moon.container.MoonScaffold
import ui.components.moon.dialog.MoonDialogDisposableScope
import ui.components.moon.moonBottomBarHeight
import ui.components.moon.screen.MoonEmptyScreen
import ui.components.moon.screen.MoonEmptyScreenType
import ui.components.moon.screen.MoonLoadingScreen
import ui.moon.MoonToastHost
import ui.moon.rememberMoonToastHostState
import ui.preview.ThemedPreview
import ui.theme.UIKit
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.SimpleTimeZone

@Composable
fun PerpsAssetDetailsScreen(
    feature: PerpsAssetDetailsFeature,
    presentAutoClose: Boolean = false,
    onOpenOrder: (PerpsOrderSide) -> Unit,
    onCashOut: (PerpsOrderSide) -> Unit,
    onOpenSizeChange: (PerpsOrderSide, PerpsSizeDirection) -> Unit,
    onOpenMarginChange: (PerpsOrderSide, Boolean) -> Unit,
    onLimitOrderEdit: (PerpsLimitOrder) -> Unit,
    onOpenHistory: () -> Unit,
    onBack: () -> Unit,
) {
    val market by feature.market.collectAsStateWorkaround()
    val about by feature.about.collectAsStateWorkaround()
    val isLoading by feature.isLoading.collectAsStateWorkaround()
    val error by feature.error.collectAsStateWorkaround()
    val tradingError by feature.tradingError.collectAsStateWorkaround()
    val tradingRetrying by feature.tradingRetrying.collectAsStateWorkaround()
    val candles by feature.chartCandles.collectAsStateWorkaround()
    val chartMode by feature.chartMode.collectAsStateWorkaround()
    val timeframe by feature.timeframe.collectAsStateWorkaround()
    val chartState by feature.chartState.collectAsStateWorkaround()
    val chartStale by feature.chartStale.collectAsStateWorkaround()
    val positionLevels by feature.positionLevels.collectAsStateWorkaround()
    val positionView by feature.positionView.collectAsStateWorkaround()
    val flags by feature.flags.collectAsStateWorkaround()
    val canTrade by feature.canTrade.collectAsStateWorkaround()
    val canOpen by feature.canOpen.collectAsStateWorkaround()
    val limitOrders by feature.limitOrders.collectAsStateWorkaround()
    val rules by feature.rules.collectAsStateWorkaround()
    val limitOrderBusy by feature.limitOrderBusy.collectAsStateWorkaround()
    val proceeding by feature.proceeding.collectAsStateWorkaround()
    val priceDecimals by feature.priceDecimals.collectAsStateWorkaround()
    val selectedCandle by feature.selectedCandle.collectAsStateWorkaround()
    val selectedChange by feature.selectedChange.collectAsStateWorkaround()
    val pill by feature.pill.collectAsStateWorkaround()
    val failure by feature.failure.collectAsStateWorkaround()
    val historyRequest by feature.historyRequest.collectAsStateWorkaround()

    val toastHost = rememberMoonToastHostState()
    val scope = rememberCoroutineScope()
    val shareFailedText = stringResource(Localization.perps_share_failed)
    val context = LocalContext.current

    LaunchedEffect(failure) {
        val pending = failure ?: return@LaunchedEffect
        feature.sendAction(PerpsAssetDetailsAction.AcknowledgeFailure(pending.operationId))
        val text = if (pending.textArg != null) {
            context.getString(pending.text, pending.textArg)
        } else {
            context.getString(pending.text)
        }
        scope.launch { toastHost.showToast(text) }
    }

    LaunchedEffect(feature) {
        feature.actionFailures.collect { text ->
            scope.launch { toastHost.showToast(context.getString(text)) }
        }
    }

    LifecycleResumeEffect(feature) {
        feature.sendAction(PerpsAssetDetailsAction.SetVisible(true))
        onPauseOrDispose {
            feature.sendAction(PerpsAssetDetailsAction.SelectChartPoint(null))
            feature.sendAction(PerpsAssetDetailsAction.SetVisible(false))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PerpsAssetDetailsContent(
            symbol = feature.symbol,
            market = market,
            about = about,
            isLoading = isLoading,
            error = error,
            tradingError = tradingError,
            tradingRetrying = tradingRetrying,
            candles = candles,
            chartMode = chartMode,
            timeframe = timeframe,
            chartState = chartState,
            chartStale = chartStale,
            positionLevels = positionLevels,
            positionView = positionView,
            flags = flags,
            canTrade = canTrade,
            canOpen = canOpen,
            limitOrders = limitOrders,
            rules = rules,
            limitOrderBusy = limitOrderBusy,
            proceeding = proceeding,
            priceDecimals = priceDecimals,
            selectedCandle = selectedCandle,
            selectedChange = selectedChange,
            pill = pill,
            historyRequest = historyRequest,
            presentAutoClose = presentAutoClose,
            onAction = feature::sendAction,
            onOpenOrder = onOpenOrder,
            onCashOut = onCashOut,
            onOpenSizeChange = onOpenSizeChange,
            onOpenMarginChange = onOpenMarginChange,
            onLimitOrderEdit = onLimitOrderEdit,
            onShareFailed = {
                if (toastHost.currentData == null) {
                    scope.launch { toastHost.showToast(shareFailedText) }
                }
            },
            onLearnBasics = { context.openPerpsBasics() },
            onOpenHistory = onOpenHistory,
            onBack = onBack,
        )
        MoonToastHost(toastHost)
    }
}

@Composable
private fun PerpsAssetDetailsContent(
    symbol: String,
    market: PerpMarket?,
    about: String?,
    isLoading: Boolean,
    error: PerpsError?,
    tradingError: Boolean = false,
    tradingRetrying: Boolean = false,
    candles: List<PerpsCandle>,
    chartMode: PerpsChartMode,
    timeframe: PerpsChartTimeframe,
    chartState: PerpsChartSectionState,
    chartStale: Boolean,
    positionLevels: PerpsPositionLevels?,
    positionView: PerpsPositionView,
    flags: PerpsTradingFlags,
    canTrade: Boolean,
    canOpen: Boolean,
    limitOrders: List<PerpsLimitOrder>,
    rules: PerpsMarketRules?,
    limitOrderBusy: Boolean,
    priceDecimals: Int,
    selectedCandle: PerpsCandle?,
    selectedChange: PerpsChartChange?,
    pill: PerpsOperationPill?,
    proceeding: PerpsAssetDetailsTarget? = null,
    historyRequest: PerpsMarketHistoryRequest? = null,
    presentAutoClose: Boolean = false,
    onAction: (PerpsAssetDetailsAction) -> Unit,
    onOpenOrder: (PerpsOrderSide) -> Unit,
    onCashOut: (PerpsOrderSide) -> Unit,
    onOpenSizeChange: (PerpsOrderSide, PerpsSizeDirection) -> Unit,
    onOpenMarginChange: (PerpsOrderSide, Boolean) -> Unit,
    onLimitOrderEdit: (PerpsLimitOrder) -> Unit,
    onShareFailed: () -> Unit,
    onLearnBasics: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onBack: () -> Unit,
) {
    val positionSection = positionView.section
    var editChoice by remember { mutableStateOf<PerpsEditChoice?>(null) }
    var marginChooser by remember { mutableStateOf(false) }
    var autoCloseSheet by remember { mutableStateOf(false) }
    var autoCloseRequested by rememberSaveable { mutableStateOf(presentAutoClose) }
    val context = LocalContext.current
    LaunchedEffect(autoCloseRequested, isLoading, error, positionSection, canTrade, flags, tradingError) {
        val waiting = tradingError ||
            positionSection is PerpsPositionSection.Loading ||
            positionSection is PerpsPositionSection.Failed ||
            (positionSection as? PerpsPositionSection.Open)?.detail?.autoCloseKnown == false
        if (!autoCloseRequested || isLoading || error != null || !canTrade || waiting) {
            return@LaunchedEffect
        }
        if (positionSection is PerpsPositionSection.Open && flags.autoCloseEnabled) {
            autoCloseSheet = true
        }
        autoCloseRequested = false
    }
    var limitOrderSheet by remember { mutableStateOf<PerpsLimitOrder?>(null) }
    var shareAt by remember { mutableStateOf<Long?>(null) }
    val detail = (positionSection as? PerpsPositionSection.Open)?.detail
    val positionSide = detail?.position?.side?.toOrderSide()
    val autoCloseMark = detail?.position?.markPrice ?: market?.price
    val sheetShown = autoCloseSheet && detail != null && positionSide != null && autoCloseMark != null
    val scrollState = rememberScrollState()

    LaunchedEffect(sheetShown) {
        onAction(PerpsAssetDetailsAction.SetAutoCloseSheetOpen(sheetShown))
        if (!sheetShown) {
            autoCloseSheet = false
        }
    }
    val showDivider by remember {
        derivedStateOf { scrollState.value > 0 }
    }

    MoonScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            PerpsAssetDetailsTopBar(
                title = symbol,
                pill = pill,
                showDivider = showDivider,
                onLearnBasics = onLearnBasics,
                onBack = onBack,
            )
        },
    ) {
        val bottomOverlayHeight = moonBottomBarHeight()
        Box {
            when {
                error != null -> MoonEmptyScreen(
                    text = stringResource(Localization.perps_load_failed),
                    description = perpsErrorDescription(error),
                    type = MoonEmptyScreenType.Error,
                    buttonText = stringResource(Localization.perps_retry),
                    onButtonClick = { onAction(PerpsAssetDetailsAction.Retry) },
                )

                market != null && !isLoading -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(bottom = bottomOverlayHeight),
                ) {
                    PerpsPriceHeader(
                        market = market,
                        chartMode = chartMode,
                        priceDecimals = priceDecimals,
                        selectedCandle = selectedCandle,
                        selectedChange = selectedChange,
                    )
                    PerpsChart(
                        candles = candles,
                        mode = chartMode,
                        timeframe = timeframe,
                        positionLevels = positionLevels,
                        priceDecimals = priceDecimals,
                        sectionState = chartState,
                        isStale = chartStale,
                        onCrosshairPoint = { point ->
                            onAction(PerpsAssetDetailsAction.SelectChartPoint(point))
                        },
                        onReachedLeftEdge = { onAction(PerpsAssetDetailsAction.LoadOlderCandles) },
                        onRetry = { onAction(PerpsAssetDetailsAction.RetryChart) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                    )
                    PerpsChartControls(
                        timeframe = timeframe,
                        chartMode = chartMode,
                        onSelectTimeframe = { selected ->
                            onAction(PerpsAssetDetailsAction.SelectTimeframe(selected))
                        },
                        onToggleMode = { onAction(PerpsAssetDetailsAction.ToggleChartMode) },
                    )
                    MoonDivider()
                    PerpsPositionSection(
                        section = positionSection,
                        card = positionView.card,
                        flags = flags,
                        canTrade = canTrade,
                        onAction = onAction,
                        onShare = { shareAt = System.currentTimeMillis() },
                        onAdjustMargin = {
                            val side = positionSide
                            val single = flags.addMarginEnabled != flags.removeMarginEnabled
                            if (side != null && single) {
                                onOpenMarginChange(side, flags.addMarginEnabled)
                            } else {
                                marginChooser = true
                            }
                        },
                        onAutoClose = { autoCloseSheet = true },
                    )
                    if (limitOrders.isNotEmpty()) {
                        PerpsLimitOrdersSection(
                            orders = limitOrders,
                            priceDecimals = priceDecimals,
                            enabled = canTrade && flags.cancelEnabled && !limitOrderBusy,
                            onOrderClick = { limitOrderSheet = it },
                        )
                    }
                    PerpsOrdersSection(
                        section = positionSection,
                        card = positionView.card,
                        flags = flags,
                        canTrade = canTrade,
                        onAutoClose = { autoCloseSheet = true },
                    )
                    if (historyRequest != null) {
                        koinInject<PerpsMarketHistorySection>().Content(
                            request = historyRequest,
                            market = market,
                            onSeeAll = onOpenHistory,
                        )
                    }
                    PerpsInfoSection(market = market)
                    PerpsAboutSection(symbol = symbol, about = about)
                }

                else -> MoonLoadingScreen()
            }

            if (market != null && !isLoading && error == null) {
                if (!tradingError) {
                    val firstLimitOrder = limitOrders.firstOrNull()
                    PerpsAssetDetailsBottomBar(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        section = positionSection,
                        flags = flags,
                        canTrade = canTrade,
                        canOpen = canOpen,
                        limitOrder = firstLimitOrder,
                        limitOrderEditable = firstLimitOrder?.isEditable(rules) == true,
                        limitOrderBusy = limitOrderBusy,
                        onLimitOrderEdit = onLimitOrderEdit,
                        onLimitOrderCancel = { onAction(PerpsAssetDetailsAction.CancelLimitOrder(context, it.orderIndex)) },
                        proceeding = proceeding,
                        onOpenOrder = onOpenOrder,
                        onCashOut = onCashOut,
                        onEdit = { side, directions ->
                            val single = directions.singleOrNull()
                            if (single != null) {
                                onOpenSizeChange(side, single)
                            } else {
                                editChoice = PerpsEditChoice(side = side, directions = directions)
                            }
                        },
                    )
                } else {
                    PerpsTradingErrorBar(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        retrying = tradingRetrying,
                        onRetry = { onAction(PerpsAssetDetailsAction.Retry) },
                    )
                }
            }
        }
    }

    editChoice?.let { choice ->
        PerpsEditPositionDialog(
            side = choice.side,
            directions = choice.directions,
            onSelect = { direction -> onOpenSizeChange(choice.side, direction) },
            onClose = { editChoice = null },
        )
    }

    if (marginChooser && positionSide != null) {
        PerpsAdjustMarginDialog(
            canAdd = flags.addMarginEnabled,
            canReduce = flags.removeMarginEnabled,
            onSelect = { add -> onOpenMarginChange(positionSide, add) },
            onClose = { marginChooser = false },
        )
    }

    if (sheetShown) {
        MoonDialogDisposableScope {
            val sheetFeature = koinViewModel<PerpsPositionAutoCloseFeature>(
                parameters = { parametersOf(detail.position.marketIndex) },
            )
            val submit by sheetFeature.submit.collectAsStateWorkaround()
            var submitted by remember { mutableStateOf<PerpsAutoCloseDraft?>(null) }
            LaunchedEffect(submit.done) {
                if (submit.done) {
                    onAction(PerpsAssetDetailsAction.AutoCloseSet(detail.withLegs(submitted)))
                }
            }
            val position = detail.position
            val entryPrice = position.avgEntryPrice ?: autoCloseMark
            PerpsAutoCloseDialog(
                priceDecimals = priceDecimals,
                side = positionSide,
                bounds = PerpsAutoCloseBounds(
                    entryPrice = entryPrice,
                    leverage = position.autoCloseLeverage(),
                    currentPrice = autoCloseMark,
                    liquidationPrice = position.liquidationPrice,
                ),
                initial = detail.legsDraft(),
                estimate = autoCloseEstimate(position.size, entryPrice),
                onSet = { draft ->
                    submitted = draft
                    sheetFeature.sendAction(PerpsPositionAutoCloseAction.Submit(context, draft))
                },
                onClose = { autoCloseSheet = false },
                submit = submit,
            )
        }
    }

    limitOrderSheet?.let { order ->
        PerpsLimitOrderActionsDialog(
            order = order,
            priceDecimals = priceDecimals,
            editable = order.isEditable(rules),
            onEdit = { onLimitOrderEdit(order) },
            onCancel = { onAction(PerpsAssetDetailsAction.CancelLimitOrder(context, order.orderIndex)) },
            onClose = { limitOrderSheet = null },
        )
    }

    val shareDate = shareAt
    if (shareDate != null && detail != null && market != null) {
        PerpsSharePositionDialog(
            card = PerpsShareCard(
                name = market.name ?: market.symbol,
                symbol = market.symbol,
                iconUrl = market.iconUrl,
                side = detail.position.side,
                leverage = detail.position.leverage,
                roiPct = detail.position.roiPct,
                isProfit = (detail.position.roiPct ?: BigDecimal.ZERO).signum() >= 0,
                entryPrice = detail.position.avgEntryPrice,
                currentPrice = detail.position.markPrice ?: market.price,
                date = shareDate,
            ),
            onShared = { success ->
                if (!success) {
                    onShareFailed()
                }
            },
            onClose = { shareAt = null },
        )
    }
}

private data class PerpsEditChoice(
    val side: PerpsOrderSide,
    val directions: Set<PerpsSizeDirection>,
)

@Composable
private fun PerpsAssetDetailsTopBar(
    title: String,
    pill: PerpsOperationPill?,
    showDivider: Boolean,
    onLearnBasics: () -> Unit,
    onBack: () -> Unit,
) {
    MoonTopAppBar(
        modifier = Modifier.statusBarsPadding(),
        title = {
            if (pill != null) {
                PerpsOperationPillView(pill = pill)
            } else {
                MoonTopAppBarTitle(text = title)
            }
        },
        onTitleClick = onLearnBasics.takeIf { pill == null },
        subtitle = if (pill == null) {
            {
                MoonTopAppBarSubtitle(
                    text = stringResource(Localization.perps_perpetual),
                    color = UIKit.colorScheme.text.accent,
                )
                Spacer(modifier = Modifier.width(4.dp))
                MoonItemIcon(
                    painter = painterResource(UIKitIcon.ic_information_circle_16),
                    color = UIKit.colorScheme.text.accent,
                )
            }
        } else {
            null
        },
        navigationIconRes = UIKitIcon.ic_chevron_left_16,
        onNavigationClick = onBack,
        showDivider = showDivider,
    )
}

@Composable
private fun PerpsOperationPillView(pill: PerpsOperationPill) {
    val done = pill.state == PerpsOperationPillState.DONE

    MoonBadgeButton(
        content = {
            if (done) {
                MoonItemIcon(
                    painter = painterResource(UIKitIcon.ic_done_16),
                    color = UIKit.colorScheme.accent.green,
                )
            } else {
                MoonLoader(modifier = Modifier.size(16.dp))
            }
            Text(
                text = perpsOperationPillText(pill = pill, done = done),
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.text.primary,
            )
        },
    )
}

@Composable
private fun perpsOperationPillText(pill: PerpsOperationPill, done: Boolean): String {
    val sideText = stringResource(
        if (pill.side == PerpsOrderSide.LONG) {
            Localization.perps_long
        } else {
            Localization.perps_short
        }
    )
    val leverageText = stringResource(Localization.perps_leverage_value_pattern, pill.leverage)
    val leverageSuffix = if (pill.leverage > 0) {
        stringResource(Localization.perps_operation_leverage_suffix, leverageText)
    } else {
        ""
    }

    return when (pill.kind) {
        PerpsOperationKind.OPEN -> stringResource(
            if (done) {
                Localization.perps_operation_opened_pattern
            } else {
                Localization.perps_operation_opening_pattern
            },
            pill.symbol,
            sideText,
            pill.amount,
            leverageText,
        )

        PerpsOperationKind.OPEN_LIMIT -> stringResource(
            if (done) {
                Localization.perps_operation_placed_pattern
            } else {
                Localization.perps_operation_placing_pattern
            },
            pill.symbol,
            sideText,
            pill.amount,
            leverageText,
        )

        PerpsOperationKind.CLOSE -> stringResource(
            if (done) {
                Localization.perps_operation_closed_pattern
            } else {
                Localization.perps_operation_closing_pattern
            },
            pill.symbol,
            sideText.lowercase(),
            leverageSuffix,
        )

        PerpsOperationKind.ADD -> stringResource(
            if (done) {
                Localization.perps_operation_increased_pattern
            } else {
                Localization.perps_operation_increasing_pattern
            },
            pill.symbol,
            sideText.lowercase(),
            leverageSuffix,
        )

        PerpsOperationKind.REDUCE -> stringResource(
            if (done) {
                Localization.perps_operation_reduced_pattern
            } else {
                Localization.perps_operation_reducing_pattern
            },
            pill.symbol,
            sideText.lowercase(),
            leverageSuffix,
        )

        PerpsOperationKind.MARGIN_ADD -> stringResource(
            if (done) {
                Localization.perps_operation_margin_added_pattern
            } else {
                Localization.perps_operation_margin_adding_pattern
            },
            pill.amount,
        )

        PerpsOperationKind.MARGIN_REMOVE -> stringResource(
            if (done) {
                Localization.perps_operation_margin_reduced_pattern
            } else {
                Localization.perps_operation_margin_reducing_pattern
            },
            pill.amount,
        )

        PerpsOperationKind.AUTO_CLOSE -> stringResource(
            if (done) {
                Localization.perps_operation_auto_close_updated
            } else {
                Localization.perps_operation_auto_close_updating
            }
        )

        PerpsOperationKind.ORDER_CANCEL -> stringResource(
            if (done) {
                Localization.perps_operation_order_cancelled
            } else {
                Localization.perps_operation_order_cancelling
            }
        )

        PerpsOperationKind.ORDER_MODIFY -> stringResource(
            if (done) {
                Localization.perps_operation_order_updated
            } else {
                Localization.perps_operation_order_updating
            }
        )
    }
}

@Composable
private fun PerpsPriceHeader(
    market: PerpMarket,
    chartMode: PerpsChartMode,
    priceDecimals: Int,
    selectedCandle: PerpsCandle?,
    selectedChange: PerpsChartChange?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 12.dp),
        ) {
            PerpsAssetIcon(
                symbol = market.symbol,
                iconUrl = market.iconUrl,
                size = 56.dp,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                // Reserves the tallest readout: the chart is a WebView and cannot be overdrawn.
                .defaultMinSize(minHeight = 66.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            when {
                selectedCandle == null -> PerpsPriceChangeBlock(
                    price = market.price?.let { formatUsd(it) } ?: PERPS_EMPTY_VALUE,
                    percent = market.priceChange24hPercent?.let { formatSignedPercent(it) }
                        ?: PERPS_EMPTY_VALUE,
                    amount = market.priceChange24hAbsolute()?.let { formatSignedUsd(it) },
                    changeColor = perpsChangeColor(market.priceChange24hPercent),
                )

                chartMode == PerpsChartMode.LINE -> PerpsLineReadout(
                    candle = selectedCandle,
                    change = selectedChange,
                    priceDecimals = priceDecimals,
                )

                else -> PerpsCandleReadout(
                    candle = selectedCandle,
                    change = selectedChange,
                    priceDecimals = priceDecimals,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun PerpsPriceChangeBlock(
    price: String,
    percent: String,
    amount: String?,
    changeColor: Color,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = price,
            style = UIKit.typography.h2,
            color = UIKit.colorScheme.text.primary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = percent,
                style = UIKit.typography.body2,
                color = changeColor,
            )
            if (amount != null) {
                Text(
                    text = amount,
                    style = UIKit.typography.body2,
                    color = changeColor.copy(alpha = 0.48f),
                )
            }
        }
    }
}

@Composable
private fun PerpsLineReadout(
    candle: PerpsCandle,
    change: PerpsChartChange?,
    priceDecimals: Int,
) {
    val isPositive = change?.isPositive ?: true
    val changeColor = if (isPositive) {
        UIKit.colorScheme.accent.green
    } else {
        UIKit.colorScheme.accent.red
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = perpsCandleDateTime(candle.time),
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.secondary,
            maxLines = 1,
            softWrap = false,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatUsdPrice(candle.close, priceDecimals),
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.text.primary,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = stringResource(
                    Localization.perps_chart_change_pattern,
                    formatSignedUsd(BigDecimal.valueOf(change?.amount ?: 0.0)),
                    formatSignedPercent(BigDecimal.valueOf(change?.percent ?: 0.0)),
                ),
                style = UIKit.typography.body2,
                color = changeColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun PerpsCandleReadout(
    candle: PerpsCandle,
    change: PerpsChartChange?,
    priceDecimals: Int,
) {
    val isPositive = change?.isPositive ?: true
    val closeColor = if (isPositive) {
        UIKit.colorScheme.accent.green
    } else {
        UIKit.colorScheme.accent.red
    }
    val openLabel = stringResource(Localization.perps_chart_open)
    val closeLabel = stringResource(Localization.perps_chart_close)
    val volumeLabel = stringResource(Localization.perps_chart_volume)
    val highLabel = stringResource(Localization.perps_chart_high)
    val lowLabel = stringResource(Localization.perps_chart_low)
    val leadingLabels = remember(openLabel, closeLabel) { listOf(openLabel, closeLabel) }
    val trailingLabels = remember(volumeLabel, highLabel, lowLabel) {
        listOf(volumeLabel, highLabel, lowLabel)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = perpsCandleDateTime(candle.time),
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.text.secondary,
                maxLines = 1,
                softWrap = false,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PerpsSizedChartTag(text = openLabel, sizingLabels = leadingLabels)
                Text(
                    text = formatUsdPrice(candle.open, priceDecimals),
                    style = UIKit.typography.body2,
                    color = UIKit.colorScheme.text.primary,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PerpsSizedChartTag(text = closeLabel, sizingLabels = leadingLabels, color = closeColor)
                Text(
                    text = formatUsdPrice(candle.close, priceDecimals),
                    style = UIKit.typography.body2,
                    color = UIKit.colorScheme.text.primary,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = formatSignedPercent(BigDecimal.valueOf(change?.percent ?: 0.0)),
                    style = UIKit.typography.body2,
                    color = closeColor,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            PerpsCandleReadoutValue(
                value = candle.volumeUsd?.let { formatCompactUsd(BigDecimal.valueOf(it)) }
                    ?: PERPS_EMPTY_VALUE,
                label = volumeLabel,
                sizingLabels = trailingLabels,
            )
            PerpsCandleReadoutValue(
                value = formatUsdPrice(candle.high, priceDecimals),
                label = highLabel,
                sizingLabels = trailingLabels,
            )
            PerpsCandleReadoutValue(
                value = formatUsdPrice(candle.low, priceDecimals),
                label = lowLabel,
                sizingLabels = trailingLabels,
            )
        }
    }
}

@Composable
private fun PerpsCandleReadoutValue(
    value: String,
    label: String,
    sizingLabels: List<String>,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value,
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.secondary,
            maxLines = 1,
            softWrap = false,
        )
        PerpsSizedChartTag(text = label, sizingLabels = sizingLabels)
    }
}

@Composable
private fun PerpsSizedChartTag(
    text: String,
    sizingLabels: List<String>,
    color: Color? = null,
) {
    Box(modifier = Modifier.width(IntrinsicSize.Max)) {
        sizingLabels.forEach { label ->
            PerpsChartTag(text = label, modifier = Modifier.alpha(0f).clearAndSetSemantics {})
        }
        PerpsChartTag(text = text, modifier = Modifier.fillMaxWidth(), color = color)
    }
}

@Composable
private fun PerpsChartTag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val background = color?.copy(alpha = 0.16f) ?: UIKit.colorScheme.background.contentTint
    Text(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .padding(horizontal = 5.dp)
            .padding(top = 4.dp, bottom = 3.dp),
        text = text.uppercase(),
        style = UIKit.typography.body4CAPS,
        color = color ?: UIKit.colorScheme.text.secondary,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
    )
}

@Composable
private fun perpsCandleDateTime(timeSeconds: Long): String {
    val locale = Locale.getDefault()
    val offsetSeconds = perpsChartZoneOffsetSeconds()
    val formatter = remember(locale, offsetSeconds) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "d MMM HH:mm"), locale).apply {
            timeZone = SimpleTimeZone((offsetSeconds * 1000L).toInt(), "")
        }
    }
    return formatter.format(Date(timeSeconds * 1000L))
}

@Composable
private fun PerpsChartControls(
    timeframe: PerpsChartTimeframe,
    chartMode: PerpsChartMode,
    onSelectTimeframe: (PerpsChartTimeframe) -> Unit,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val secondary = UIKit.colorScheme.buttonSecondary
    val pageColor = UIKit.colorScheme.background.page

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 92.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PerpsChartTimeframe.entries.forEach { entry ->
                val isSelected = entry == timeframe
                val backgroundColor = if (isSelected) {
                    secondary.primaryBackground
                } else {
                    Color.Transparent
                }
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(backgroundColor)
                        .clickable { onSelectTimeframe(entry) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(entry.label),
                        style = UIKit.typography.label2,
                        color = secondary.primaryForeground,
                        maxLines = 1,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(38.dp)
                .height(36.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, pageColor))),
        )
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp)
                .size(width = 72.dp, height = 40.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(20.dp),
                    ambientColor = Color.Black,
                    spotColor = Color.Black,
                )
                .clip(RoundedCornerShape(20.dp))
                .background(UIKit.colorScheme.background.contentAlternate)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PerpsChartMode.entries.forEach { mode ->
                val isSelected = mode == chartMode
                val tabColor = if (isSelected) {
                    UIKit.colorScheme.background.contentAttention
                } else {
                    Color.Transparent
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(tabColor)
                        .clickable(enabled = !isSelected, onClick = onToggleMode),
                    contentAlignment = Alignment.Center,
                ) {
                    MoonItemIcon(
                        painter = painterResource(
                            if (mode == PerpsChartMode.LINE) {
                                UIKitIcon.ic_chart_line_16
                            } else {
                                UIKitIcon.ic_chart_candle_16
                            },
                        ),
                        size = 16.dp,
                        color = if (isSelected) {
                            UIKit.colorScheme.icon.primary
                        } else {
                            UIKit.colorScheme.icon.secondary
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PerpsInfoSection(market: PerpMarket) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MoonBundleTitleCell(title = stringResource(Localization.perps_info))
        MoonBundleCell {
            Column {
                MoonPropertyBigCell(
                    title = {
                        MoonPropertyTitle(
                            title = stringResource(Localization.perps_volume_24h),
                            infoTooltip = stringResource(Localization.perps_volume_24h_tooltip),
                        )
                    },
                    content = {
                        MoonPropertyValue(
                            title = market.volume24h?.let { formatCompactUsd(it) }
                                ?: PERPS_EMPTY_VALUE,
                        )
                    },
                )
                MoonItemDivider()
                MoonPropertyBigCell(
                    title = {
                        MoonPropertyTitle(
                            title = stringResource(Localization.perps_open_interest),
                            infoTooltip = stringResource(Localization.perps_open_interest_tooltip),
                        )
                    },
                    content = {
                        MoonPropertyValue(
                            title = market.openInterestUsd?.let { formatCompactUsd(it) }
                                ?: PERPS_EMPTY_VALUE,
                        )
                    },
                )
                MoonItemDivider()
                MoonPropertyBigCell(
                    title = {
                        MoonPropertyTitle(
                            title = stringResource(Localization.perps_funding),
                            infoTooltip = stringResource(Localization.perps_funding_tooltip),
                        )
                    },
                    content = {
                        MoonPropertyValue(
                            title = market.fundingRateHourly?.let { formatFundingPercent(it) }
                                ?: PERPS_EMPTY_VALUE,
                        )
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PerpsAboutSection(symbol: String, about: String?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MoonBundleTitleCell(title = stringResource(Localization.perps_about))
        MoonBundleCell {
            MoonExpandableText(
                modifier = Modifier.padding(16.dp),
                text = about ?: stringResource(Localization.perps_about_placeholder, symbol),
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.text.primary,
                maxLines = 3,
                showMoreText = stringResource(Localization.perps_more),
                showMoreColor = UIKit.colorScheme.text.accent,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PerpsTradingErrorBar(
    retrying: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MoonBottomBar(modifier = modifier.background(UIKit.colorScheme.background.page)) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(Localization.perps_trading_load_failed),
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.secondary,
        )
        MoonAccentButton(
            text = stringResource(Localization.perps_retry),
            size = ButtonSizeLarge,
            buttonColors = ButtonColorsPrimary,
            loading = retrying,
            onClick = onRetry,
        )
    }
}

@Composable
private fun PerpsAssetDetailsBottomBar(
    section: PerpsPositionSection,
    flags: PerpsTradingFlags,
    canTrade: Boolean,
    canOpen: Boolean,
    limitOrder: PerpsLimitOrder?,
    limitOrderEditable: Boolean,
    limitOrderBusy: Boolean,
    onLimitOrderEdit: (PerpsLimitOrder) -> Unit,
    onLimitOrderCancel: (PerpsLimitOrder) -> Unit,
    proceeding: PerpsAssetDetailsTarget?,
    onOpenOrder: (PerpsOrderSide) -> Unit,
    onCashOut: (PerpsOrderSide) -> Unit,
    onEdit: (PerpsOrderSide, Set<PerpsSizeDirection>) -> Unit,
    modifier: Modifier = Modifier,
) {
    MoonBottomBar(modifier = modifier) {
        if (section is PerpsPositionSection.Open) {
            val side = section.detail.position.side.toOrderSide()
            val directions = buildSet {
                if (flags.openEnabled) {
                    add(PerpsSizeDirection.ADD)
                }
                if (flags.closeEnabled) {
                    add(PerpsSizeDirection.REDUCE)
                }
            }
            MoonAccentButton(
                modifier = Modifier.weight(1f),
                text = stringResource(Localization.perps_edit),
                size = ButtonSizeLarge,
                buttonColors = ButtonColorsTertiary,
                enabled = canTrade && directions.isNotEmpty(),
                loading = proceeding is PerpsAssetDetailsTarget.SizeChange,
                onClick = { onEdit(side, directions) },
            )
            MoonAccentButton(
                modifier = Modifier.weight(1f),
                text = stringResource(Localization.perps_cash_out),
                size = ButtonSizeLarge,
                buttonColors = ButtonColorsPrimary,
                enabled = canTrade && flags.closeEnabled,
                loading = proceeding is PerpsAssetDetailsTarget.CashOut,
                onClick = { onCashOut(side) },
            )
        } else if (section !is PerpsPositionSection.Loading && limitOrder != null) {
            val enabled = canTrade && flags.cancelEnabled && !limitOrderBusy
            MoonAccentButton(
                modifier = Modifier.weight(1f),
                text = stringResource(Localization.edit),
                size = ButtonSizeLarge,
                buttonColors = ButtonColorsTertiary,
                enabled = enabled && limitOrderEditable,
                onClick = { onLimitOrderEdit(limitOrder) },
            )
            MoonAccentButton(
                modifier = Modifier.weight(1f),
                text = stringResource(Localization.cancel),
                size = ButtonSizeLarge,
                buttonColors = ButtonColorsPrimary,
                enabled = enabled,
                onClick = { onLimitOrderCancel(limitOrder) },
            )
        } else {
            val enabled = canOpen && section != PerpsPositionSection.Loading
            MoonAccentButton(
                modifier = Modifier.weight(1f),
                text = stringResource(Localization.perps_long),
                size = ButtonSizeLarge,
                buttonColors = ButtonColorsPrimary,
                enabled = enabled,
                loading = proceeding == PerpsAssetDetailsTarget.Order(PerpsOrderSide.LONG),
                onClick = { onOpenOrder(PerpsOrderSide.LONG) },
            )
            MoonAccentButton(
                modifier = Modifier.weight(1f),
                text = stringResource(Localization.perps_short),
                size = ButtonSizeLarge,
                buttonColors = ButtonColorsPrimary,
                enabled = enabled,
                loading = proceeding == PerpsAssetDetailsTarget.Order(PerpsOrderSide.SHORT),
                onClick = { onOpenOrder(PerpsOrderSide.SHORT) },
            )
        }
    }
}

private val previewMarket = PerpMarket(
    marketIndex = 1,
    symbol = "BTC",
    maxLeverage = 40,
    price = BigDecimal("66141.70"),
    priceChange24hPercent = BigDecimal("4.37"),
    volume24h = BigDecimal("2360000000"),
    openInterestUsd = BigDecimal("1700000000"),
    fundingRateHourly = BigDecimal("0.0000032385"),
    priceDecimals = 2,
    sizeDecimals = 6,
)

@Preview
@Composable
private fun PerpsAssetDetailsScreenPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsAssetDetailsContent(
            symbol = previewMarket.symbol,
            market = previewMarket,
            about = null,
            isLoading = false,
            error = null,
            candles = previewCandles,
            chartMode = PerpsChartMode.CANDLE,
            timeframe = PerpsChartTimeframe.H1,
            chartState = PerpsChartSectionState.READY,
            chartStale = false,
            positionLevels = null,
            positionView = PerpsPositionView(
                section = PerpsPositionSection.Hidden,
                card = null,
            ),
            flags = PerpsTradingFlags.ALL_ENABLED,
            canTrade = true,
            canOpen = true,
            limitOrders = emptyList(),
            rules = null,
            limitOrderBusy = false,
            priceDecimals = 2,
            selectedCandle = null,
            selectedChange = null,
            pill = null,
            onAction = {},
            onOpenOrder = {},
            onCashOut = {},
            onOpenSizeChange = { _, _ -> },
            onOpenMarginChange = { _, _ -> },
            onLimitOrderEdit = {},
            onShareFailed = {},
            onLearnBasics = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun PerpsAssetDetailsCrosshairPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsAssetDetailsContent(
            symbol = previewMarket.symbol,
            market = previewMarket,
            about = null,
            isLoading = false,
            error = null,
            candles = previewCandles,
            chartMode = PerpsChartMode.CANDLE,
            timeframe = PerpsChartTimeframe.H1,
            chartState = PerpsChartSectionState.READY,
            chartStale = false,
            positionLevels = null,
            positionView = PerpsPositionView(
                section = PerpsPositionSection.Hidden,
                card = null,
            ),
            flags = PerpsTradingFlags.ALL_ENABLED,
            canTrade = true,
            canOpen = true,
            limitOrders = emptyList(),
            rules = null,
            limitOrderBusy = false,
            priceDecimals = 2,
            selectedCandle = previewCandles.last(),
            selectedChange = PerpsChartChange(percent = -0.06, amount = -38.3, isPositive = false),
            pill = null,
            onAction = {},
            onOpenOrder = {},
            onCashOut = {},
            onOpenSizeChange = { _, _ -> },
            onOpenMarginChange = { _, _ -> },
            onLimitOrderEdit = {},
            onShareFailed = {},
            onLearnBasics = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun PerpsAssetDetailsScreenErrorPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsAssetDetailsContent(
            symbol = "BTC",
            market = null,
            about = null,
            isLoading = false,
            error = PerpsError.ServiceUnavailable,
            candles = emptyList(),
            chartMode = PerpsChartMode.CANDLE,
            timeframe = PerpsChartTimeframe.H1,
            chartState = PerpsChartSectionState.FAILED,
            chartStale = false,
            positionLevels = null,
            positionView = PerpsPositionView(
                section = PerpsPositionSection.Hidden,
                card = null,
            ),
            flags = PerpsTradingFlags.ALL_ENABLED,
            canTrade = true,
            canOpen = true,
            limitOrders = emptyList(),
            rules = null,
            limitOrderBusy = false,
            priceDecimals = 2,
            selectedCandle = null,
            selectedChange = null,
            pill = null,
            onAction = {},
            onOpenOrder = {},
            onCashOut = {},
            onOpenSizeChange = { _, _ -> },
            onOpenMarginChange = { _, _ -> },
            onLimitOrderEdit = {},
            onShareFailed = {},
            onLearnBasics = {},
            onBack = {},
        )
    }
}

private val previewPosition = PerpsOpenPosition(
    id = "lighter:1",
    marketIndex = 1,
    symbol = "BTC",
    side = PerpsPositionSide.LONG,
    size = BigDecimal("0.008164"),
    positionValue = BigDecimal("540.00"),
    avgEntryPrice = BigDecimal("66541.70"),
    markPrice = BigDecimal("66141.70"),
    liquidationPrice = BigDecimal("64141.75"),
    liquidationDistancePct = BigDecimal("-6.9"),
    margin = BigDecimal("20.50"),
    equity = BigDecimal("21.00"),
    unrealizedPnl = BigDecimal("0.50"),
    roiPct = BigDecimal("0.01"),
    leverage = 27,
    fundingPaid = BigDecimal("0"),
)

private val previewTakeProfit = PerpsAutoCloseLeg(
    kind = PerpsAutoCloseLegKind.TAKE_PROFIT,
    orderIndex = 1L,
    clientOrderIndex = null,
    triggerPrice = BigDecimal("68141.70"),
    sharePct = BigDecimal("100"),
    projectedRoiPct = BigDecimal("15.5"),
)

private val previewStopLoss = PerpsAutoCloseLeg(
    kind = PerpsAutoCloseLegKind.STOP_LOSS,
    orderIndex = 2L,
    clientOrderIndex = null,
    triggerPrice = BigDecimal("64720.16"),
    sharePct = BigDecimal("100"),
    projectedRoiPct = BigDecimal("-1.5"),
)

private fun PerpsLimitOrder.isEditable(rules: PerpsMarketRules?): Boolean {
    val remaining = remainingBase ?: return true
    val minBase = rules?.minSizeBase
    val minQuote = rules?.minSizeQuote
    val notional = limitPrice?.multiply(remaining)

    return (minBase == null || remaining >= minBase) && (minQuote == null || notional == null || notional >= minQuote)
}

private val previewLimitOrder = PerpsLimitOrder(
    orderIndex = 1L,
    clientOrderIndex = null,
    side = PerpsPositionSide.LONG,
    remainingBase = BigDecimal("0.002"),
    limitPrice = BigDecimal("65000.00"),
)

@Preview
@Composable
private fun PerpsAssetDetailsPositionPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsAssetDetailsContent(
            symbol = previewMarket.symbol,
            market = previewMarket,
            about = null,
            isLoading = false,
            error = null,
            candles = previewCandles,
            chartMode = PerpsChartMode.CANDLE,
            timeframe = PerpsChartTimeframe.H1,
            chartState = PerpsChartSectionState.READY,
            chartStale = false,
            positionLevels = null,
            positionView = PerpsPositionView(
                section = PerpsPositionSection.Open(
                    PerpsPositionDetail(
                        position = previewPosition,
                        autoCloseKnown = true,
                        takeProfit = null,
                        stopLoss = null,
                    )
                ),
                card = PerpsPositionDetail(
                    position = previewPosition,
                    autoCloseKnown = true,
                    takeProfit = null,
                    stopLoss = null,
                ).toCardUi(symbol = previewMarket.symbol, sizeDecimals = previewMarket.sizeDecimals, hiddenBalance = false),
            ),
            flags = PerpsTradingFlags.ALL_ENABLED,
            canTrade = true,
            canOpen = true,
            limitOrders = emptyList(),
            rules = null,
            limitOrderBusy = false,
            priceDecimals = 2,
            selectedCandle = null,
            selectedChange = null,
            pill = null,
            onAction = {},
            onOpenOrder = {},
            onCashOut = {},
            onOpenSizeChange = { _, _ -> },
            onOpenMarginChange = { _, _ -> },
            onLimitOrderEdit = {},
            onShareFailed = {},
            onLearnBasics = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun PerpsAssetDetailsPositionLegsPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsAssetDetailsContent(
            symbol = previewMarket.symbol,
            market = previewMarket,
            about = null,
            isLoading = false,
            error = null,
            candles = previewCandles,
            chartMode = PerpsChartMode.CANDLE,
            timeframe = PerpsChartTimeframe.H1,
            chartState = PerpsChartSectionState.READY,
            chartStale = false,
            positionLevels = null,
            positionView = PerpsPositionView(
                section = PerpsPositionSection.Open(
                    PerpsPositionDetail(
                        position = previewPosition,
                        autoCloseKnown = true,
                        takeProfit = previewTakeProfit,
                        stopLoss = previewStopLoss,
                    )
                ),
                card = PerpsPositionDetail(
                    position = previewPosition,
                    autoCloseKnown = true,
                    takeProfit = previewTakeProfit,
                    stopLoss = previewStopLoss,
                ).toCardUi(symbol = previewMarket.symbol, sizeDecimals = previewMarket.sizeDecimals, hiddenBalance = false),
            ),
            flags = PerpsTradingFlags.ALL_ENABLED,
            canTrade = true,
            canOpen = true,
            limitOrders = listOf(previewLimitOrder),
            rules = null,
            limitOrderBusy = false,
            priceDecimals = 2,
            selectedCandle = null,
            selectedChange = null,
            pill = null,
            onAction = {},
            onOpenOrder = {},
            onCashOut = {},
            onOpenSizeChange = { _, _ -> },
            onOpenMarginChange = { _, _ -> },
            onLimitOrderEdit = {},
            onShareFailed = {},
            onLearnBasics = {},
            onBack = {},
        )
    }
}

@Preview
@Composable
private fun PerpsAssetDetailsPositionFailedPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsAssetDetailsContent(
            symbol = previewMarket.symbol,
            market = previewMarket,
            about = null,
            isLoading = false,
            error = null,
            candles = previewCandles,
            chartMode = PerpsChartMode.CANDLE,
            timeframe = PerpsChartTimeframe.H1,
            chartState = PerpsChartSectionState.READY,
            chartStale = false,
            positionLevels = null,
            positionView = PerpsPositionView(
                section = PerpsPositionSection.Failed(PerpsError.ServiceUnavailable),
                card = null,
            ),
            flags = PerpsTradingFlags.ALL_ENABLED,
            canTrade = true,
            canOpen = true,
            limitOrders = emptyList(),
            rules = null,
            limitOrderBusy = false,
            priceDecimals = 2,
            selectedCandle = null,
            selectedChange = null,
            pill = null,
            onAction = {},
            onOpenOrder = {},
            onCashOut = {},
            onOpenSizeChange = { _, _ -> },
            onOpenMarginChange = { _, _ -> },
            onLimitOrderEdit = {},
            onShareFailed = {},
            onLearnBasics = {},
            onBack = {},
        )
    }
}
