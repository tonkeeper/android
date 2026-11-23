package com.tonapps.deposit.multicoin

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import ui.components.moon.MoonCircleIcon
import ui.theme.UIKit

@Composable
internal fun AccentCircleIcon(painter: Painter) {
    val color = UIKit.colorScheme.accent.blue
    val background = remember(color) { color.copy(alpha = 0.13f) }
    MoonCircleIcon(
        modifier = Modifier
            .clip(CircleShape)
            .background(background),
        size = 44.dp,
        iconSize = 28.dp,
        painter = painter,
        color = Color.Transparent,
    )
}
