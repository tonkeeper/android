package com.tonapps.tonkeeper.ui.screen.events.compose.history.ui.placeholder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ui.theme.modifiers.shimmer

@Composable
fun TxEventItemPlaceholder(
    shimmerPhaseProvider: () -> Float,
    modifier: Modifier = Modifier,
    height: Dp = (78..98).random().dp,
) {

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .shimmer(shimmerPhaseProvider())
    )
}