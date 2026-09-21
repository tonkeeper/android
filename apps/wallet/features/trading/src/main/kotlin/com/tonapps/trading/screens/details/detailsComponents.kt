package com.tonapps.trading.screens.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tonapps.extensions.locale
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.icu.Formatter
import com.tonapps.trading.formatChartTime
import com.tonapps.trading.percentDiffColor
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.ButtonColorsPrimary
import ui.components.moon.ButtonSizeLarge
import ui.components.moon.MoonAccentButton
import ui.components.moon.MoonBottomBar
import ui.components.moon.MoonFavoriteIconLottie
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonTextShimmer
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.painterResource
import ui.preview.ThemedPreview
import ui.theme.Dimens
import ui.theme.Shapes
import ui.theme.UIKit
import uikit.chart.ChartPeriod
import uikit.chart.ChartPoint
import uikit.chart.ChartView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun ChartSection(
    imageUrl: String,
    chainImageUrl: String?,
    currencyCode: String,
    chartData: List<ChartPoint>?,
    chartPeriod: ChartPeriod,
    onPeriodChange: (ChartPeriod) -> Unit,
    onOpenStaking: () -> Unit,
    maxStakingApyFormatted: String? = null,
) {
    var selectedPoint by remember { mutableStateOf<ChartPoint?>(null) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MoonCutBadgedBox(
                badge = if (chainImageUrl != null) {
                    { MoonItemImage(image = chainImageUrl, size = 20.dp) }
                } else {
                    null
                },
                direction = BadgeDirection.EndBottom,
            ) {
                MoonItemImage(
                    image = imageUrl,
                    placeholder = painterResource(UIKitIcon.ic_illustration),
                    size = 56.dp,
                )
            }

            if (maxStakingApyFormatted != null) {
                MaxStakingApyLabel(
                    formattedApy = maxStakingApyFormatted,
                    onClick = onOpenStaking,
                )
            }
        }

        ChartHeader(
            selectedPoint = selectedPoint,
            chartData = chartData,
            chartPeriod = chartPeriod,
            currencyCode = currencyCode,
        )
        Spacer(modifier = Modifier.height(4.dp))
        AssetChart(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            currencyCode = currencyCode,
            chartPeriod = chartPeriod,
            data = chartData,
            isSquare = chartPeriod == ChartPeriod.hour,
            onPointSelected = { selectedPoint = it },
        )
        ChartPeriodSelector(
            chartPeriod = chartPeriod,
            onPeriodSelected = onPeriodChange,
        )
    }
}

@Composable
private fun MaxStakingApyLabel(
    formattedApy: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(Shapes.medium)
            .background(UIKit.colorScheme.accent.green.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoonItemIcon(
            painter = painterResource(UIKitIcon.ic_staking_16),
            color = UIKit.colorScheme.accent.green
        )
        Text(
            text = stringResource(Localization.earn_apy, formattedApy),
            style = UIKit.typography.label2,
            color = UIKit.colorScheme.accent.green,
        )
    }
}

@Composable
private fun AssetChart(
    modifier: Modifier = Modifier,
    currencyCode: String,
    chartPeriod: ChartPeriod,
    data: List<ChartPoint>?,
    isSquare: Boolean = false,
    onPointSelected: ((ChartPoint?) -> Unit)? = null,
) {
    val locale = LocalContext.current.locale
    val formatAxisPrice = remember(currencyCode) {
        { price: Float ->
            CurrencyFormatter.formatFiat(currencyCode, price.toBigDecimal()).toString()
        }
    }
    val formatAxisTime = remember(chartPeriod, locale) {
        val formatter = chartAxisGuideTimeFormatter(locale, chartPeriod)
        return@remember { ts: Long ->
            if (ts <= 0L) {
                ""
            } else {
                formatter.format(Instant.ofEpochSecond(ts).atZone(ZoneId.systemDefault()))
            }
        }
    }
    val emptyText = stringResource(Localization.no_price_data_available)
    AndroidView(
        modifier = modifier,
        factory = { context -> ChartView(context) },
        update = { view ->
            view.emptyText = emptyText
            view.setData(data, isSquare)
            view.onPointSelected = onPointSelected
            view.formatAxisPrice = formatAxisPrice
            view.formatAxisTime = formatAxisTime
        },
    )
}

@Composable
private fun ChartPeriodSelector(
    modifier: Modifier = Modifier,
    chartPeriod: ChartPeriod,
    onPeriodSelected: (ChartPeriod) -> Unit,
) {
    val periods = ChartPeriod.entries
    val selectedIndex = periods.indexOf(chartPeriod).let { if (it >= 0) it else 0 }
    val secondary = UIKit.colorScheme.buttonSecondary

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(36.dp),
    ) {
        val tabWidth = maxWidth / periods.size
        val pillOffset by animateDpAsState(
            targetValue = tabWidth * selectedIndex,
            animationSpec = tween(durationMillis = 200),
            label = "chartPeriodPill",
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .offset(x = pillOffset)
                    .width(tabWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(18.dp))
                    .background(secondary.primaryBackground),
            )
            Row(modifier = Modifier.fillMaxSize()) {
                periods.forEach { period ->
                    val interactionSource = remember(period) { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onPeriodSelected(period) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = period.title,
                            style = UIKit.typography.label2,
                            color = secondary.primaryForeground,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartDiffRow(
    modifier: Modifier = Modifier,
    percentText: String,
    deltaPriceText: String,
    periodCaption: String,
    diffColor: Color,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = percentText,
            style = UIKit.typography.body2,
            color = diffColor,
            maxLines = 1,
            overflow = TextOverflow.Visible,
        )
        Text(
            modifier = Modifier.alpha(0.48f),
            text = deltaPriceText,
            style = UIKit.typography.body2,
            color = diffColor,
            maxLines = 1,
            overflow = TextOverflow.Visible,
        )
        Text(
            modifier = Modifier.weight(1f),
            text = periodCaption,
            style = UIKit.typography.body2,
            color = UIKit.colorScheme.text.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private data class RollingDiffDisplay(
    val percentValue: Float,
    val percentText: String,
    val deltaPriceText: String,
    val periodCaption: String,
    val diffColor: Color,
)

@Composable
private fun AnimatedChartDiff(
    modifier: Modifier = Modifier,
    percentValue: Float,
    percentText: String,
    deltaPriceText: String,
    periodCaption: String,
) {
    val offsetMotion = tween<IntOffset>(durationMillis = 200, easing = FastOutSlowInEasing)
    val fadeMotion = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
    val display = RollingDiffDisplay(
        percentValue = percentValue,
        percentText = percentText,
        deltaPriceText = deltaPriceText,
        periodCaption = periodCaption,
        diffColor = percentText.percentDiffColor(),
    )
    AnimatedContent(
        targetState = display,
        modifier = modifier
            .graphicsLayer { clip = false },
        transitionSpec = {
            val upward = targetState.percentValue > initialState.percentValue
            if (upward) {
                (
                        slideInVertically(
                            animationSpec = offsetMotion,
                            initialOffsetY = { it / 2 }) +
                                fadeIn(animationSpec = fadeMotion)
                        ) togetherWith (
                        slideOutVertically(
                            animationSpec = offsetMotion,
                            targetOffsetY = { -it / 2 }) +
                                fadeOut(animationSpec = fadeMotion)
                        ) using SizeTransform(clip = false)
            } else {
                (
                        slideInVertically(
                            animationSpec = offsetMotion,
                            initialOffsetY = { -(it / 2) }) +
                                fadeIn(animationSpec = fadeMotion)
                        ) togetherWith (
                        slideOutVertically(
                            animationSpec = offsetMotion,
                            targetOffsetY = { it / 2 }) +
                                fadeOut(animationSpec = fadeMotion)
                        ) using SizeTransform(clip = false)
            }
        },
        label = "animatedChartDiff",
    ) { state ->
        ChartDiffRow(
            percentText = state.percentText,
            deltaPriceText = state.deltaPriceText,
            periodCaption = state.periodCaption,
            diffColor = state.diffColor,
        )
    }
}

private fun ChartPeriod.showsTimeInChartCaption(): Boolean =
    this == ChartPeriod.hour || this == ChartPeriod.day || this == ChartPeriod.week

private fun ChartPeriod.showsYearInChartCaption(): Boolean =
    this == ChartPeriod.year || this == ChartPeriod.halfYear

@Composable
private fun ChartHeader(
    selectedPoint: ChartPoint?,
    chartData: List<ChartPoint>?,
    chartPeriod: ChartPeriod,
    currencyCode: String,
) {
    val context = LocalContext.current
    val locale = context.locale
    val points = chartData?.filter { !it.isEmpty } ?: emptyList()
    val lastPoint = points.lastOrNull()
    val activePrice = when {
        selectedPoint != null && !selectedPoint.isEmpty -> selectedPoint.price
        lastPoint != null -> lastPoint.price
        else -> null
    }

    val priceText = if (activePrice != null) {
        CurrencyFormatter.formatFiat(currencyCode, activePrice.toBigDecimal()).toString()
    } else {
        ""
    }

    val formattedChartTime =
        if (selectedPoint != null && selectedPoint.date > 0) {
            selectedPoint.date.formatChartTime(
                locale,
                includeTime = chartPeriod.showsTimeInChartCaption(),
                includeYear = chartPeriod.showsYearInChartCaption()
            )
        } else {
            ""
        }
    val periodOrTimeCaption = formattedChartTime.ifEmpty {
        stringResource(chartPeriodCaptionRes(chartPeriod))
    }

    val (percent, deltaPrice) = if (points.isEmpty() || activePrice == null) {
        0f to 0f
    } else {
        val firstPrice = points.first().price
        val percent = (activePrice - firstPrice) / firstPrice * 100f
        percent to (activePrice - firstPrice)
    }

    val percentText = if (activePrice != null) Formatter.percent(percent) else ""
    val deltaPriceText =
        CurrencyFormatter.formatFiat(currencyCode, deltaPrice.toBigDecimal()).toString()

    val isScrubbing = selectedPoint != null && !selectedPoint.isEmpty

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (chartData == null) {
            MoonTextShimmer(
                text = "$0,000.00",
                style = UIKit.typography.h2,
                cornerRadius = 100.dp,
            )
            MoonTextShimmer(
                text = "+0.00% +$0.00 Last month",
                style = UIKit.typography.body2,
                cornerRadius = 12.dp,
            )
        } else {
            Text(text = priceText, style = UIKit.typography.h2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (chartData.isEmpty()) {
                    Text(text = "", style = UIKit.typography.body2)
                } else if (isScrubbing) {
                    ChartDiffRow(
                        percentText = percentText,
                        deltaPriceText = deltaPriceText,
                        periodCaption = periodOrTimeCaption,
                        diffColor = percentText.percentDiffColor(),
                    )
                } else {
                    AnimatedChartDiff(
                        modifier = Modifier.fillMaxWidth(),
                        percentValue = percent,
                        percentText = percentText,
                        deltaPriceText = deltaPriceText,
                        periodCaption = periodOrTimeCaption,
                    )
                }
            }
        }
    }
}

private fun chartAxisGuideTimeFormatter(locale: Locale, period: ChartPeriod): DateTimeFormatter {
    return when (period) {
        ChartPeriod.hour, ChartPeriod.day ->
            DateTimeFormatter.ofPattern("HH:mm").withLocale(locale)

        else ->
            DateTimeFormatter.ofPattern("d MMM").withLocale(locale)
    }
}

private fun chartPeriodCaptionRes(period: ChartPeriod): Int = when (period) {
    ChartPeriod.hour -> Localization.chart_period_last_hour
    ChartPeriod.day -> Localization.chart_period_last_day
    ChartPeriod.week -> Localization.chart_period_last_week
    ChartPeriod.month -> Localization.chart_period_last_month
    ChartPeriod.halfYear -> Localization.chart_period_last_6_months
    ChartPeriod.year -> Localization.chart_period_last_year
}

@Composable
internal fun AssetDetailsBottomBar(
    modifier: Modifier = Modifier,
    hasBalance: Boolean,
    onBuyClick: () -> Unit = {},
    onSellClick: () -> Unit = {},
) {
    MoonBottomBar(modifier = modifier) {
        MoonAccentButton(
            modifier = Modifier.weight(1f),
            text = stringResource(Localization.buy),
            size = ButtonSizeLarge,
            buttonColors = ButtonColorsPrimary,
            onClick = onBuyClick,
        )
        if (hasBalance) {
            MoonAccentButton(
                modifier = Modifier.weight(1f),
                text = stringResource(Localization.sell),
                size = ButtonSizeLarge,
                buttonColors = ButtonColorsPrimary,
                onClick = onSellClick,
            )
        }
    }
}

@Composable
internal fun AssetActionButtonsRow(
    modifier: Modifier = Modifier,
    showSend: Boolean,
    showCashBuy: Boolean,
    showCashSell: Boolean,
    onSendClick: () -> Unit = {},
    onReceiveClick: () -> Unit = {},
    onCashBuyClick: () -> Unit = {},
    onCashSellClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (showSend) {
            AssetActionButton(
                modifier = Modifier.weight(1f),
                painter = painterResource(UIKitIcon.ic_tray_arrow_up_16),
                title = stringResource(Localization.send),
                onClick = onSendClick,
            )
        }
        AssetActionButton(
            modifier = Modifier.weight(1f),
            painter = painterResource(UIKitIcon.ic_qr_code_16),
            title = stringResource(Localization.receive),
            onClick = onReceiveClick,
        )
        if (showCashBuy) {
            AssetActionButton(
                modifier = Modifier.weight(1f),
                painter = painterResource(UIKitIcon.ic_dollar_outline_plus_16),
                title = stringResource(Localization.cash_buy),
                onClick = onCashBuyClick,
            )
        }
        if (showCashSell) {
            AssetActionButton(
                modifier = Modifier.weight(1f),
                painter = painterResource(UIKitIcon.ic_dollar_outline_minus_16),
                title = stringResource(Localization.cash_sell),
                onClick = onCashSellClick,
            )
        }
    }
}

@Composable
private fun AssetActionButton(
    modifier: Modifier = Modifier,
    painter: Painter,
    title: String,
    onClick: () -> Unit,
) {
    val colors = UIKit.colorScheme.buttonSecondary
    Column(
        modifier = modifier
            .height(60.dp)
            .clip(Shapes.medium)
            .background(colors.primaryBackground)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        MoonItemIcon(
            painter = painter,
            size = 16.dp,
            color = colors.primaryForeground,
            contentDescription = null,
        )
        Text(
            modifier = Modifier.padding(horizontal = 4.dp),
            text = title,
            style = UIKit.typography.label3,
            color = colors.primaryForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(widthDp = 360)
@Composable
private fun AssetActionButtonsRowPreview() {
    ThemedPreview {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AssetActionButtonsRow(showSend = true, showCashBuy = true, showCashSell = true)
            AssetActionButtonsRow(showSend = true, showCashBuy = false, showCashSell = false)
            AssetActionButtonsRow(showSend = false, showCashBuy = true, showCashSell = false)
            AssetActionButtonsRow(showSend = false, showCashBuy = false, showCashSell = false)
        }
    }
}

@Preview
@Composable
private fun AssetDetailsBottomBarNoBalancePreview() {
    ThemedPreview {
        AssetDetailsBottomBar(hasBalance = false)
    }
}

private const val FavoriteLottieDurationMillis = 1_183

@Composable
fun FavoriteActionIcon(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(0f) }
    var animateChanges by remember { mutableStateOf(false) }
    val inactiveColor = UIKit.colorScheme.icon.tertiary
    val accentColor = UIKit.colorScheme.accent.blue

    LaunchedEffect(isFavorite) {
        val target = if (isFavorite) 1f else 0f
        if (animateChanges) {
            progress.animateTo(
                targetValue = target,
                animationSpec = tween(FavoriteLottieDurationMillis),
            )
        } else {
            progress.snapTo(target)
        }
    }

    Box(
        modifier = modifier
            .size(Dimens.sizeAction)
            .clip(CircleShape)
            .background(UIKit.colorScheme.buttonSecondary.primaryBackground)
            .clickable {
                animateChanges = true
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        MoonFavoriteIconLottie(
            inactiveColor = inactiveColor,
            accentColor = accentColor,
            modifier = Modifier.size(32.dp),
            progress = { progress.value },
        )
    }
}
