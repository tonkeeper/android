package com.tonapps.tonkeeper.ui.screen.events.compose.history.ui.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.theme.Dimens
import ui.theme.modifiers.shimmer

@Composable
private fun TxFilterPlaceholder(
    shimmerPhaseProvider: () -> Float,
    modifier: Modifier = Modifier,
    width: Dp,
) {
    Box(
        modifier = modifier
            .width(width)
            .height(Dimens.sizeAction)
            .shimmer(shimmerPhaseProvider())
    )
}

@Composable
fun TxFiltersPlaceholder(
    shimmerPhaseProvider: () -> Float
) {
    Row(
        modifier = Modifier
            .padding(horizontal = Dimens.offsetMedium)
            .fillMaxWidth()
            .height(Dimens.heightItem),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.offsetMedium / 2)
    ) {
        for (i in 1..4) {
            val width = remember {
                (56..102).random().dp
            }

            TxFilterPlaceholder(shimmerPhaseProvider = shimmerPhaseProvider, width = width)
        }
    }
}