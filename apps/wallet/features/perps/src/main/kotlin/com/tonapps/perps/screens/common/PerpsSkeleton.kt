package com.tonapps.perps.screens.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.components.moon.cell.MoonBundleCell
import ui.components.moon.cell.defaultBundleType
import ui.preview.ThemedPreview
import ui.theme.UIKit
import ui.theme.modifiers.rememberShimmerPhase
import ui.theme.modifiers.shimmer

internal const val PERPS_SKELETON_ROW_COUNT = 8

@Composable
internal fun PerpsSkeletonBar(
    width: Dp,
    phase: State<Float>,
    modifier: Modifier = Modifier,
    height: Dp = 12.dp,
    fill: Color = UIKit.colorScheme.background.contentTint,
    highlight: Color = UIKit.colorScheme.background.contentAttention,
) {
    Spacer(
        modifier = modifier
            .size(width = width, height = height)
            .shimmer(
                phase = phase,
                cornerRadius = height / 2,
                backgroundFill = fill,
                highlightColor = highlight,
            ),
    )
}

@Composable
internal fun PerpsSkeletonCircle(
    size: Dp,
    phase: State<Float>,
    modifier: Modifier = Modifier,
    fill: Color = UIKit.colorScheme.background.contentTint,
    highlight: Color = UIKit.colorScheme.background.contentAttention,
) {
    PerpsSkeletonBar(
        modifier = modifier,
        width = size,
        height = size,
        phase = phase,
        fill = fill,
        highlight = highlight,
    )
}

@Composable
internal fun PerpsMarketSkeletonRow(phase: State<Float>) {
    PerpsSkeletonRow(
        phase = phase,
        titleWidth = 40.dp,
        titleTagWidth = 24.dp,
        subtitleWidth = 64.dp,
        valueWidth = 72.dp,
        valueSubtitleWidth = 40.dp,
    )
}

@Composable
internal fun PerpsHistorySkeletonRow(phase: State<Float>) {
    PerpsSkeletonRow(
        phase = phase,
        titleWidth = 72.dp,
        titleTagWidth = null,
        subtitleWidth = 56.dp,
        valueWidth = 48.dp,
        valueSubtitleWidth = 64.dp,
    )
}

@Composable
internal fun PerpsSkeletonRowsCard(
    count: Int,
    modifier: Modifier = Modifier,
    row: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(count) { index ->
            MoonBundleCell(position = defaultBundleType(count, index)) {
                row()
            }
        }
    }
}

@Composable
private fun PerpsSkeletonRow(
    phase: State<Float>,
    titleWidth: Dp,
    titleTagWidth: Dp?,
    subtitleWidth: Dp,
    valueWidth: Dp,
    valueSubtitleWidth: Dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PerpsSkeletonCircle(size = 44.dp, phase = phase)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PerpsSkeletonBar(width = titleWidth, phase = phase)
                if (titleTagWidth != null) {
                    PerpsSkeletonBar(width = titleTagWidth, phase = phase)
                }
            }
            PerpsSkeletonBar(
                modifier = Modifier.padding(vertical = 4.dp),
                width = subtitleWidth,
                phase = phase,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.End) {
            PerpsSkeletonBar(
                modifier = Modifier.padding(vertical = 6.dp),
                width = valueWidth,
                phase = phase,
            )
            PerpsSkeletonBar(
                modifier = Modifier.padding(vertical = 4.dp),
                width = valueSubtitleWidth,
                phase = phase,
            )
        }
    }
}

@Preview
@Composable
private fun PerpsMarketSkeletonRowsPreview() {
    ThemedPreview(isDarkOnly = true) {
        val phase = rememberShimmerPhase()
        PerpsSkeletonRowsCard(count = 3) { PerpsMarketSkeletonRow(phase = phase) }
    }
}

@Preview
@Composable
private fun PerpsHistorySkeletonRowsPreview() {
    ThemedPreview(isDarkOnly = true) {
        val phase = rememberShimmerPhase()
        PerpsSkeletonRowsCard(count = 3) { PerpsHistorySkeletonRow(phase = phase) }
    }
}
