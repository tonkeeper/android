package com.tonapps.perps.screens.details

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.tonapps.paging.collectAsStateWorkaround
import com.tonapps.perps.data.PERPS_EMPTY_VALUE
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.data.PerpsAutoCloseLeg
import com.tonapps.perps.data.PerpsAutoCloseLegKind
import com.tonapps.perps.data.PerpsCandle
import com.tonapps.perps.data.PerpsChartChange
import com.tonapps.perps.data.PerpsChartMode
import com.tonapps.perps.data.PerpsChartSectionState
import com.tonapps.perps.data.PerpsChartTimeframe
import com.tonapps.perps.data.PerpsError
import com.tonapps.perps.data.PerpsLimitOrder
import com.tonapps.perps.data.PerpsOpenPosition
import com.tonapps.perps.data.PerpsPositionDetail
import com.tonapps.perps.data.PerpsPositionLevels
import com.tonapps.perps.data.PerpsPositionSection
import com.tonapps.perps.data.PerpsPositionSide
import com.tonapps.perps.data.PerpsTradingFlags
import com.tonapps.perps.data.formatCompactUsd
import com.tonapps.perps.data.formatFundingPercent
import com.tonapps.perps.data.formatSignedPercent
import com.tonapps.perps.data.formatSignedUsd
import com.tonapps.perps.data.formatUsd
import com.tonapps.perps.data.formatUsdPrice
import com.tonapps.perps.data.priceChange24hAbsolute
import com.tonapps.perps.screens.markets.PerpsAssetIcon
import com.tonapps.perps.screens.markets.perpsChangeColor
import com.tonapps.perps.screens.markets.perpsErrorDescription
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.launch
import ui.components.moon.ButtonColorsPrimary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonActionIcon
import ui.components.moon.MoonBottomBar
import ui.components.moon.MoonDivider
import ui.components.moon.MoonExpandableText
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonTopAppBar
import ui.components.moon.MoonTopAppBarSubtitle
import ui.components.moon.MoonTopAppBarTitle
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundleTitleCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.cell.MoonPropertyTitle
import ui.components.moon.cell.MoonPropertyValue
import ui.components.moon.container.MoonScaffold
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

@Composable
fun PerpsAssetDetailsScreen(
    feature: PerpsAssetDetailsFeature,
    onBack: () -> Unit,
) {
    val market by feature.market.collectAsStateWorkaround()
    val about by feature.about.collectAsStateWorkaround()
    val isLoading by feature.isLoading.collectAsStateWorkaround()
    val error by feature.error.collectAsStateWorkaround()
    val candles by feature.chartCandles.collectAsStateWorkaround()
    val chartMode by feature.chartMode.collectAsStateWorkaround()
    val timeframe by feature.timeframe.collectAsStateWorkaround()
    val chartState by feature.chartState.collectAsStateWorkaround()
    val chartStale by feature.chartStale.collectAsStateWorkaround()
    val positionLevels by feature.positionLevels.collectAsStateWorkaround()
    val positionSection by feature.positionSection.collectAsStateWorkaround()
    val flags by feature.flags.collectAsStateWorkaround()
    val limitOrders by feature.limitOrders.collectAsStateWorkaround()
    val priceDecimals by feature.priceDecimals.collectAsStateWorkaround()
    val sizeDecimals by feature.sizeDecimals.collectAsStateWorkaround()
    val selectedCandle by feature.selectedCandle.collectAsStateWorkaround()
    val selectedChange by feature.selectedChange.collectAsStateWorkaround()

    val toastHost = rememberMoonToastHostState()
    val scope = rememberCoroutineScope()
    val comingSoonText = stringResource(Localization.perps_coming_soon)

    LifecycleResumeEffect(feature) {
        feature.sendAction(PerpsAssetDetailsAction.SetVisible(true))
        onPauseOrDispose {
            feature.sendAction(PerpsAssetDetailsAction.SelectChartTime(null))
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
            candles = candles,
            chartMode = chartMode,
            timeframe = timeframe,
            chartState = chartState,
            chartStale = chartStale,
            positionLevels = positionLevels,
            positionSection = positionSection,
            flags = flags,
            limitOrders = limitOrders,
            priceDecimals = priceDecimals,
            sizeDecimals = sizeDecimals,
            selectedCandle = selectedCandle,
            selectedChange = selectedChange,
            onAction = feature::sendAction,
            onComingSoon = {
                if (toastHost.currentData == null) {
                    scope.launch { toastHost.showToast(comingSoonText) }
                }
            },
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
    candles: List<PerpsCandle>,
    chartMode: PerpsChartMode,
    timeframe: PerpsChartTimeframe,
    chartState: PerpsChartSectionState,
    chartStale: Boolean,
    positionLevels: PerpsPositionLevels?,
    positionSection: PerpsPositionSection,
    flags: PerpsTradingFlags,
    limitOrders: List<PerpsLimitOrder>,
    priceDecimals: Int,
    sizeDecimals: Int?,
    selectedCandle: PerpsCandle?,
    selectedChange: PerpsChartChange?,
    onAction: (PerpsAssetDetailsAction) -> Unit,
    onComingSoon: () -> Unit,
    onBack: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val showDivider by remember {
        derivedStateOf { scrollState.value > 0 }
    }

    MoonScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            PerpsAssetDetailsTopBar(
                title = symbol,
                showDivider = showDivider,
                onComingSoon = onComingSoon,
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
                        onCrosshairTime = { time ->
                            onAction(PerpsAssetDetailsAction.SelectChartTime(time))
                        },
                        onRetry = { onAction(PerpsAssetDetailsAction.RetryChart) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp),
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
                        flags = flags,
                        symbol = symbol,
                        sizeDecimals = sizeDecimals,
                        onAction = onAction,
                        onComingSoon = onComingSoon,
                    )
                    PerpsOrdersSection(
                        section = positionSection,
                        flags = flags,
                        limitOrders = limitOrders,
                        symbol = symbol,
                        sizeDecimals = sizeDecimals,
                        onComingSoon = onComingSoon,
                    )
                    PerpsInfoSection(market = market)
                    PerpsAboutSection(symbol = symbol, about = about)
                }

                else -> MoonLoadingScreen()
            }

            if (market != null && !isLoading && error == null) {
                PerpsAssetDetailsBottomBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    section = positionSection,
                    flags = flags,
                    onComingSoon = onComingSoon,
                )
            }
        }
    }
}

@Composable
private fun PerpsAssetDetailsTopBar(
    title: String,
    showDivider: Boolean,
    onComingSoon: () -> Unit,
    onBack: () -> Unit,
) {
    MoonTopAppBar(
        modifier = Modifier.statusBarsPadding(),
        title = { MoonTopAppBarTitle(text = title) },
        onTitleClick = onComingSoon,
        subtitle = {
            MoonTopAppBarSubtitle(
                text = stringResource(Localization.perps_perpetual),
                color = UIKit.colorScheme.text.accent,
            )
            Spacer(modifier = Modifier.width(4.dp))
            MoonItemIcon(
                painter = painterResource(UIKitIcon.ic_information_circle_16),
                color = UIKit.colorScheme.text.accent,
            )
        },
        navigationIconRes = UIKitIcon.ic_chevron_left_16,
        onNavigationClick = onBack,
        hasCustomActions = true,
        actions = {
            MoonActionIcon(
                painter = painterResource(UIKitIcon.ic_ellipsis_16),
                onClick = onComingSoon,
                tintColor = UIKit.colorScheme.buttonSecondary.primaryForeground,
                backgroundColor = UIKit.colorScheme.buttonSecondary.primaryBackground,
            )
        },
        showDivider = showDivider,
    )
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
                // Reserved height so swapping in the crosshair readout never shifts the chart.
                .defaultMinSize(minHeight = 72.dp),
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
    trailing: String? = null,
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
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = UIKit.typography.body2,
                    color = UIKit.colorScheme.text.secondary,
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
    PerpsPriceChangeBlock(
        price = formatUsdPrice(candle.close, priceDecimals),
        percent = formatSignedPercent(BigDecimal.valueOf(change?.percent ?: 0.0)),
        amount = formatSignedUsd(BigDecimal.valueOf(change?.amount ?: 0.0)),
        changeColor = changeColor,
        trailing = perpsCandleDateTime(candle.time),
    )
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
    val leadingLabels = listOf(
        stringResource(Localization.perps_chart_open),
        stringResource(Localization.perps_chart_close),
    )
    val trailingLabels = listOf(
        stringResource(Localization.perps_chart_volume),
        stringResource(Localization.perps_chart_high),
        stringResource(Localization.perps_chart_low),
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = perpsCandleDateTime(candle.time),
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.text.secondary,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PerpsChartTagSlot(sizingLabels = leadingLabels, alignment = Alignment.CenterEnd) {
                    PerpsChartTag(text = stringResource(Localization.perps_chart_open))
                }
                Text(
                    text = formatUsdPrice(candle.open, priceDecimals),
                    style = UIKit.typography.body2,
                    color = UIKit.colorScheme.text.primary,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PerpsChartTagSlot(sizingLabels = leadingLabels, alignment = Alignment.CenterEnd) {
                    PerpsChartTag(
                        text = stringResource(Localization.perps_chart_close),
                        color = closeColor,
                    )
                }
                Text(
                    text = formatUsdPrice(candle.close, priceDecimals),
                    style = UIKit.typography.body2,
                    color = UIKit.colorScheme.text.primary,
                )
                Text(
                    text = formatSignedPercent(BigDecimal.valueOf(change?.percent ?: 0.0)),
                    style = UIKit.typography.body2,
                    color = closeColor,
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
                label = stringResource(Localization.perps_chart_volume),
                sizingLabels = trailingLabels,
            )
            PerpsCandleReadoutValue(
                value = formatUsdPrice(candle.high, priceDecimals),
                label = stringResource(Localization.perps_chart_high),
                sizingLabels = trailingLabels,
            )
            PerpsCandleReadoutValue(
                value = formatUsdPrice(candle.low, priceDecimals),
                label = stringResource(Localization.perps_chart_low),
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
        )
        PerpsChartTagSlot(sizingLabels = sizingLabels, alignment = Alignment.CenterStart) {
            PerpsChartTag(text = label)
        }
    }
}

@Composable
private fun PerpsChartTagSlot(
    sizingLabels: List<String>,
    alignment: Alignment,
    tag: @Composable () -> Unit,
) {
    Box(contentAlignment = alignment) {
        sizingLabels.forEach { label ->
            PerpsChartTag(text = label, modifier = Modifier.alpha(0f))
        }
        tag()
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
    )
}

@Composable
private fun perpsCandleDateTime(timeSeconds: Long): String {
    val locale = Locale.getDefault()
    val formatter = remember(locale) {
        SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "d MMM HH:mm"), locale)
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PerpsChartTimeframe.entries.forEach { entry ->
            val isSelected = entry == timeframe
            val backgroundColor = if (isSelected) {
                secondary.primaryBackground
            } else {
                Color.Transparent
            }
            val textColor = if (isSelected) {
                secondary.primaryForeground
            } else {
                UIKit.colorScheme.text.secondary
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(CircleShape)
                    .background(backgroundColor)
                    .clickable { onSelectTimeframe(entry) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.label,
                    style = UIKit.typography.label2,
                    color = textColor,
                    maxLines = 1,
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(secondary.primaryBackground)
                .clickable(onClick = onToggleMode),
            contentAlignment = Alignment.Center,
        ) {
            PerpsChartModeGlyph(chartMode = chartMode)
        }
    }
}

// The glyph advertises the mode the button switches to, not the current one.
@Composable
private fun PerpsChartModeGlyph(chartMode: PerpsChartMode) {
    val green = UIKit.colorScheme.accent.green
    val red = UIKit.colorScheme.accent.red

    if (chartMode == PerpsChartMode.CANDLE) {
        Canvas(modifier = Modifier.size(width = 16.dp, height = 12.dp)) {
            val path = Path().apply {
                moveTo(0f, size.height)
                lineTo(size.width * 0.33f, size.height * 0.35f)
                lineTo(size.width * 0.6f, size.height * 0.6f)
                lineTo(size.width, 0f)
            }
            drawPath(
                path = path,
                color = green,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    } else {
        Canvas(modifier = Modifier.size(width = 11.dp, height = 16.dp)) {
            drawGlyphCandle(
                centerX = 2.dp.toPx(),
                color = green,
                bodyHeight = 8.dp.toPx(),
                offsetY = 1.dp.toPx(),
            )
            drawGlyphCandle(
                centerX = 9.dp.toPx(),
                color = red,
                bodyHeight = 6.dp.toPx(),
                offsetY = -1.dp.toPx(),
            )
        }
    }
}

private fun DrawScope.drawGlyphCandle(
    centerX: Float,
    color: Color,
    bodyHeight: Float,
    offsetY: Float,
) {
    val wickHeight = 14.dp.toPx()
    val bodyWidth = 4.dp.toPx()
    val centerY = size.height / 2f + offsetY
    drawLine(
        color = color,
        start = Offset(centerX, centerY - wickHeight / 2f),
        end = Offset(centerX, centerY + wickHeight / 2f),
        strokeWidth = 1.dp.toPx(),
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(centerX - bodyWidth / 2f, centerY - bodyHeight / 2f),
        size = Size(bodyWidth, bodyHeight),
        cornerRadius = CornerRadius(1.dp.toPx()),
    )
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
                MoonDivider()
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
                MoonDivider()
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
private fun PerpsAssetDetailsBottomBar(
    section: PerpsPositionSection,
    flags: PerpsTradingFlags,
    onComingSoon: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MoonBottomBar(modifier = modifier) {
        if (section is PerpsPositionSection.Open) {
            MoonAccentButton(
                modifier = Modifier.weight(1f),
                text = stringResource(Localization.perps_edit),
                size = ButtonSizeLarge,
                buttonColors = ButtonColorsPrimary,
                onClick = onComingSoon,
            )
            MoonAccentButton(
                modifier = Modifier.weight(1f),
                text = stringResource(Localization.perps_cash_out),
                size = ButtonSizeLarge,
                buttonColors = ButtonColorsPrimary,
                enabled = flags.closeEnabled,
                onClick = onComingSoon,
            )
        } else {
            val enabled = section != PerpsPositionSection.Loading && flags.openEnabled
            MoonAccentButton(
                modifier = Modifier.weight(1f),
                text = stringResource(Localization.perps_long),
                size = ButtonSizeLarge,
                buttonColors = ButtonColorsPrimary,
                enabled = enabled,
                onClick = onComingSoon,
            )
            MoonAccentButton(
                modifier = Modifier.weight(1f),
                text = stringResource(Localization.perps_short),
                size = ButtonSizeLarge,
                buttonColors = ButtonColorsPrimary,
                enabled = enabled,
                onClick = onComingSoon,
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
            positionSection = PerpsPositionSection.Hidden,
            flags = PerpsTradingFlags.ALL_ENABLED,
            limitOrders = emptyList(),
            priceDecimals = 2,
            sizeDecimals = previewMarket.sizeDecimals,
            selectedCandle = null,
            selectedChange = null,
            onAction = {},
            onComingSoon = {},
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
            positionSection = PerpsPositionSection.Hidden,
            flags = PerpsTradingFlags.ALL_ENABLED,
            limitOrders = emptyList(),
            priceDecimals = 2,
            sizeDecimals = previewMarket.sizeDecimals,
            selectedCandle = previewCandles.last(),
            selectedChange = PerpsChartChange(percent = -0.06, amount = -38.3, isPositive = false),
            onAction = {},
            onComingSoon = {},
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
            positionSection = PerpsPositionSection.Hidden,
            flags = PerpsTradingFlags.ALL_ENABLED,
            limitOrders = emptyList(),
            priceDecimals = 2,
            sizeDecimals = previewMarket.sizeDecimals,
            selectedCandle = null,
            selectedChange = null,
            onAction = {},
            onComingSoon = {},
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
    unrealizedPnl = BigDecimal("0.50"),
    roiPct = BigDecimal("0.01"),
    leverage = 27,
    fundingPaid = BigDecimal("0"),
)

private val previewTakeProfit = PerpsAutoCloseLeg(
    kind = PerpsAutoCloseLegKind.TAKE_PROFIT,
    orderIndex = 1L,
    triggerPrice = BigDecimal("68141.70"),
    sharePct = BigDecimal("100"),
    projectedEquity = BigDecimal("24.25"),
    projectedRoiPct = BigDecimal("15.5"),
)

private val previewStopLoss = PerpsAutoCloseLeg(
    kind = PerpsAutoCloseLegKind.STOP_LOSS,
    orderIndex = 2L,
    triggerPrice = BigDecimal("64720.16"),
    sharePct = BigDecimal("100"),
    projectedEquity = BigDecimal("18.25"),
    projectedRoiPct = BigDecimal("-1.5"),
)

private val previewLimitOrder = PerpsLimitOrder(
    orderId = "1",
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
            positionSection = PerpsPositionSection.Open(
                PerpsPositionDetail(
                    position = previewPosition,
                    autoCloseKnown = true,
                    takeProfit = null,
                    stopLoss = null,
                )
            ),
            flags = PerpsTradingFlags.ALL_ENABLED,
            limitOrders = emptyList(),
            priceDecimals = 2,
            sizeDecimals = previewMarket.sizeDecimals,
            selectedCandle = null,
            selectedChange = null,
            onAction = {},
            onComingSoon = {},
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
            positionSection = PerpsPositionSection.Open(
                PerpsPositionDetail(
                    position = previewPosition,
                    autoCloseKnown = true,
                    takeProfit = previewTakeProfit,
                    stopLoss = previewStopLoss,
                )
            ),
            flags = PerpsTradingFlags.ALL_ENABLED,
            limitOrders = listOf(previewLimitOrder),
            priceDecimals = 2,
            sizeDecimals = previewMarket.sizeDecimals,
            selectedCandle = null,
            selectedChange = null,
            onAction = {},
            onComingSoon = {},
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
            positionSection = PerpsPositionSection.Failed(PerpsError.ServiceUnavailable),
            flags = PerpsTradingFlags.ALL_ENABLED,
            limitOrders = emptyList(),
            priceDecimals = 2,
            sizeDecimals = previewMarket.sizeDecimals,
            selectedCandle = null,
            selectedChange = null,
            onAction = {},
            onComingSoon = {},
            onBack = {},
        )
    }
}
