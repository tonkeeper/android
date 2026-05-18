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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import ui.components.moon.MoonActionIcon
import ui.components.moon.MoonItemIcon
import ui.components.moon.MoonItemImage
import ui.components.moon.MoonTextShimmer
import ui.components.moon.container.BadgeDirection
import ui.components.moon.container.MoonCutBadgedBox
import ui.components.popup.ActionMenu
import ui.components.popup.ComposeActionItem
import ui.painterResource
import ui.theme.Shapes
import ui.theme.UIKit
import uikit.chart.ChartPeriod
import uikit.chart.ChartPeriodView
import uikit.chart.ChartPoint
import uikit.chart.ChartView


@Composable
internal fun SectionTitle(
    modifier: Modifier = Modifier,
    text: String,
    onSeeAllClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = UIKit.typography.label1,
            color = UIKit.colorScheme.text.primary
        )
        if (onSeeAllClick != null) {
            Text(
                text = stringResource(Localization.see_all),
                modifier = Modifier.clickable { onSeeAllClick() },
                style = UIKit.typography.body2,
                color = UIKit.colorScheme.text.accent,
            )
        }
    }
}

@Composable
internal fun ChartSection(
    imageUrl: String,
    chainImageUrl: String?,
    currencyCode: String,
    chartData: List<ChartPoint>,
    chartPeriod: ChartPeriod,
    onPeriodChange: (ChartPeriod) -> Unit,
    onOpenStaking: () -> Unit,
    maxStakingApyFormatted: String? = null,
) {
    var selectedPoint by remember { mutableStateOf<ChartPoint?>(null) }

    val (minPrice, maxPrice) = remember(chartData) {
        chartData.filter { !it.isEmpty }
            .let { points ->
                val min = points.minByOrNull { it.price }?.price?.let {
                    CurrencyFormatter.formatFiat(currencyCode, it.toBigDecimal()).toString()
                } ?: ""
                val max = points.maxByOrNull { it.price }?.price?.let {
                    CurrencyFormatter.formatFiat(currencyCode, it.toBigDecimal()).toString()
                } ?: ""
                min to max
            }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (chainImageUrl != null) {
                MoonCutBadgedBox(
                    badge = {
                        MoonItemImage(image = chainImageUrl, size = 20.dp)
                    },
                    direction = BadgeDirection.EndBottom,
                ) {
                    MoonItemImage(image = imageUrl, size = 56.dp)
                }
            } else {
                MoonItemImage(image = imageUrl, size = 56.dp)
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

        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            text = maxPrice,
            textAlign = TextAlign.Right,
            style = UIKit.typography.body3,
            color = UIKit.colorScheme.text.secondary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        AssetChart(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp),
            data = chartData,
            isSquare = chartPeriod == ChartPeriod.hour,
            onPointSelected = { selectedPoint = it },
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            text = minPrice,
            textAlign = TextAlign.Right,
            style = UIKit.typography.body3,
            color = UIKit.colorScheme.text.secondary,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp), contentAlignment = Alignment.Center
        ) {
            AssetChartPeriod(
                chartPeriod = chartPeriod,
                onPeriodChange = onPeriodChange,
            )
        }
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
    data: List<ChartPoint>,
    isSquare: Boolean = false,
    onPointSelected: ((ChartPoint?) -> Unit)? = null,
) {
    AndroidView(
        modifier = modifier,
        factory = { context -> ChartView(context) },
        update = { view ->
            view.setData(data, isSquare)
            view.onPointSelected = onPointSelected
        },
    )
}

@Composable
private fun AssetChartPeriod(
    modifier: Modifier = Modifier,
    chartPeriod: ChartPeriod,
    onPeriodChange: (ChartPeriod) -> Unit,
) {
    AndroidView(
        modifier = modifier.wrapContentSize(),
        factory = { context ->
            ChartPeriodView(context).apply {
                doOnPeriodSelected = { onPeriodChange(it) }
            }
        },
        update = { view ->
            view.syncSelectedPeriodFromState(chartPeriod)
        },
    )
}

private fun ChartPeriod.showsTimeInChartCaption(): Boolean =
    this == ChartPeriod.hour || this == ChartPeriod.day || this == ChartPeriod.week

private fun ChartPeriod.showsYearInChartCaption(): Boolean =
    this == ChartPeriod.year || this == ChartPeriod.halfYear

@Composable
private fun ChartHeader(
    selectedPoint: ChartPoint?,
    chartData: List<ChartPoint>,
    chartPeriod: ChartPeriod,
    currencyCode: String,
) {
    val context = LocalContext.current
    val locale = context.locale
    val points = chartData.filter { !it.isEmpty }
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

    val diffColor = percentText.percentDiffColor()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (activePrice == null) {
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = percentText,
                    style = UIKit.typography.body2,
                    color = diffColor,
                )
                Text(
                    modifier = Modifier.alpha(0.48f),
                    text = deltaPriceText,
                    style = UIKit.typography.body2,
                    color = diffColor,
                )
                Text(
                    text = periodOrTimeCaption,
                    style = UIKit.typography.body2,
                    color = UIKit.colorScheme.text.secondary,
                )
            }
        }
    }
}

private const val AssetDetailsMoreMenuSend = "send"
private const val AssetDetailsMoreMenuReceive = "receive"

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
    onSendClick: () -> Unit = {},
    onReceiveClick: () -> Unit = {},
) {
    var moreMenuExpanded by remember { mutableStateOf(false) }
    val moreMenuItems = buildList {
        if (hasBalance) {
            add(
                ComposeActionItem(
                    id = AssetDetailsMoreMenuSend,
                    text = stringResource(Localization.send),
                    iconPainter = painterResource(UIKitIcon.ic_tray_arrow_up_16),
                ),
            )
        }
        add(
            ComposeActionItem(
                id = AssetDetailsMoreMenuReceive,
                text = stringResource(Localization.receive),
                iconPainter = painterResource(UIKitIcon.ic_qr_code_16),
            ),
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
        Box {
            MoonActionIcon(
                painter = painterResource(UIKitIcon.ic_ellipsis_16),
                onClick = { moreMenuExpanded = !moreMenuExpanded },
                tintColor = UIKit.colorScheme.buttonSecondary.primaryForeground,
                size = ButtonSizeLarge.height,
                contentDescription = stringResource(Localization.more),
            )
            ActionMenu(
                expanded = moreMenuExpanded,
                onDismissRequest = { moreMenuExpanded = false },
                items = moreMenuItems,
                onItemClick = { item ->
                    moreMenuExpanded = false
                    when (item.id) {
                        AssetDetailsMoreMenuSend -> onSendClick()
                        AssetDetailsMoreMenuReceive -> onReceiveClick()
                        else -> Unit
                    }
                },
            )
        }
    }
}
