package com.tonapps.perps.screens.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonapps.perps.data.PerpsChartMode
import com.tonapps.perps.data.PerpsChartTimeframe
import com.tonapps.perps.screens.common.PerpsHistorySkeletonRow
import com.tonapps.perps.screens.common.PerpsSkeletonBar
import com.tonapps.perps.screens.common.PerpsSkeletonCircle
import com.tonapps.perps.screens.common.PerpsSkeletonRowsCard
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonDivider
import ui.components.moon.MoonItemDivider
import ui.components.moon.MoonItemIcon
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.MoonBundleTitleCell
import ui.components.moon.cell.MoonPropertyBigCell
import ui.components.moon.cell.MoonPropertyTitle
import ui.preview.ThemedPreview
import ui.theme.UIKit
import ui.theme.modifiers.rememberShimmerPhase
import ui.theme.modifiers.shimmer

private const val HISTORY_SKELETON_ROW_COUNT = 3

@Composable
internal fun PerpsAssetDetailsSkeleton(
    expectsOpenPosition: Boolean,
    timeframe: PerpsChartTimeframe,
    chartMode: PerpsChartMode,
    onSelectTimeframe: (PerpsChartTimeframe) -> Unit,
    onToggleMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val phase = rememberShimmerPhase()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        PerpsPriceHeaderSkeleton(phase = phase)
        PerpsChartSkeleton(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            phase = phase,
        )
        PerpsChartControls(
            timeframe = timeframe,
            chartMode = chartMode,
            onSelectTimeframe = onSelectTimeframe,
            onToggleMode = onToggleMode,
        )
        MoonDivider()
        if (expectsOpenPosition) {
            PerpsPositionSkeleton(phase = phase)
            PerpsMarketHistorySkeleton(phase = phase)
        }
        PerpsInfoSkeleton(phase = phase)
    }
}

@Composable
internal fun PerpsChartSkeleton(
    phase: State<Float>,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier = modifier
            .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 24.dp)
            .shimmer(phase = phase, cornerRadius = 16.dp),
    )
}

@Composable
internal fun PerpsPositionSkeleton(phase: State<Float>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MoonBundleTitleCell(title = stringResource(Localization.perps_your_position))
        MoonBundleCell {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                PerpsSkeletonBar(
                    modifier = Modifier.padding(vertical = 4.dp),
                    width = 79.dp,
                    height = 24.dp,
                    phase = phase,
                )
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PerpsSkeletonBar(width = 47.dp, phase = phase)
                    PerpsSkeletonBar(width = 47.dp, phase = phase)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        MoonBundleCell {
            Column {
                PerpsSkeletonPropertyRow(
                    title = stringResource(Localization.perps_size),
                    tooltip = stringResource(Localization.perps_size_tooltip),
                    valueWidth = 35.dp,
                    phase = phase,
                    valuePostfix = {
                        MoonItemIcon(
                            painter = painterResource(UIKitIcon.ic_swap_vertical_16),
                            color = UIKit.colorScheme.icon.secondary,
                        )
                    },
                )
                MoonItemDivider()
                MoonPropertyBigCell(
                    title = {
                        Column {
                            MoonPropertyTitle(
                                title = stringResource(Localization.perps_liquidation_price),
                                infoTooltip = stringResource(Localization.perps_liquidation_price_tooltip),
                            )
                            PerpsSkeletonBar(
                                modifier = Modifier.padding(vertical = 4.dp),
                                width = 165.dp,
                                phase = phase,
                            )
                        }
                    },
                    content = { PerpsSkeletonValue(width = 88.dp, phase = phase) },
                )
                MoonItemDivider()
                PerpsSkeletonPropertyRow(
                    title = stringResource(Localization.perps_mark_price),
                    tooltip = stringResource(Localization.perps_mark_price_tooltip),
                    valueWidth = 88.dp,
                    phase = phase,
                )
                MoonItemDivider()
                PerpsSkeletonPropertyRow(
                    title = stringResource(Localization.perps_entry_price),
                    tooltip = stringResource(Localization.perps_entry_price_tooltip),
                    valueWidth = 88.dp,
                    phase = phase,
                )
                MoonItemDivider()
                PerpsSkeletonPropertyRow(
                    title = stringResource(Localization.perps_funding),
                    tooltip = stringResource(Localization.perps_funding_tooltip),
                    valueWidth = 53.dp,
                    phase = phase,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PerpsPriceHeaderSkeleton(phase: State<Float>) {
    val fill = UIKit.colorScheme.background.content
    val highlight = UIKit.colorScheme.background.contentTint
    Column(modifier = Modifier.fillMaxWidth()) {
        PerpsSkeletonCircle(
            modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 12.dp),
            size = 56.dp,
            phase = phase,
            fill = fill,
            highlight = highlight,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .defaultMinSize(minHeight = 66.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PerpsSkeletonBar(
                modifier = Modifier.padding(vertical = 4.dp),
                width = 150.dp,
                height = 24.dp,
                phase = phase,
                fill = fill,
                highlight = highlight,
            )
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PerpsSkeletonBar(width = 48.dp, phase = phase, fill = fill, highlight = highlight)
                PerpsSkeletonBar(width = 60.dp, phase = phase, fill = fill, highlight = highlight)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun PerpsMarketHistorySkeleton(phase: State<Float>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MoonBundleTitleCell(title = stringResource(Localization.transaction_history))
        PerpsSkeletonRowsCard(count = HISTORY_SKELETON_ROW_COUNT) {
            PerpsHistorySkeletonRow(phase = phase)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PerpsInfoSkeleton(phase: State<Float>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        MoonBundleTitleCell(title = stringResource(Localization.perps_info))
        MoonBundleCell {
            Column {
                PerpsSkeletonPropertyRow(
                    title = stringResource(Localization.perps_volume_24h),
                    tooltip = stringResource(Localization.perps_volume_24h_tooltip),
                    valueWidth = 53.dp,
                    phase = phase,
                )
                MoonItemDivider()
                PerpsSkeletonPropertyRow(
                    title = stringResource(Localization.perps_open_interest),
                    tooltip = stringResource(Localization.perps_open_interest_tooltip),
                    valueWidth = 53.dp,
                    phase = phase,
                )
                MoonItemDivider()
                PerpsSkeletonPropertyRow(
                    title = stringResource(Localization.perps_funding),
                    tooltip = stringResource(Localization.perps_funding_tooltip),
                    valueWidth = 97.dp,
                    phase = phase,
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PerpsSkeletonPropertyRow(
    title: String,
    tooltip: String,
    valueWidth: Dp,
    phase: State<Float>,
    valuePostfix: (@Composable () -> Unit)? = null,
) {
    MoonPropertyBigCell(
        title = { MoonPropertyTitle(title = title, infoTooltip = tooltip) },
        content = { PerpsSkeletonValue(width = valueWidth, phase = phase) },
        valuePostfix = valuePostfix,
    )
}

@Composable
private fun PerpsSkeletonValue(width: Dp, phase: State<Float>) {
    PerpsSkeletonBar(
        modifier = Modifier.padding(vertical = 6.dp),
        width = width,
        phase = phase,
    )
}

@Preview
@Composable
private fun PerpsAssetDetailsSkeletonPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsAssetDetailsSkeleton(
            expectsOpenPosition = false,
            timeframe = PerpsChartTimeframe.M5,
            chartMode = PerpsChartMode.CANDLE,
            onSelectTimeframe = {},
            onToggleMode = {},
        )
    }
}

@Preview(heightDp = 1600)
@Composable
private fun PerpsAssetDetailsSkeletonWithPositionPreview() {
    ThemedPreview(isDarkOnly = true) {
        PerpsAssetDetailsSkeleton(
            expectsOpenPosition = true,
            timeframe = PerpsChartTimeframe.M5,
            chartMode = PerpsChartMode.CANDLE,
            onSelectTimeframe = {},
            onToggleMode = {},
        )
    }
}
