package com.tonapps.wallet.features.events.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.components.moon.MoonTextShimmer
import ui.theme.UIKit

@Composable
fun ActivityHeaderCell(
    modifier: Modifier = Modifier,
    text: String,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = UIKit.typography.label1,
            color = UIKit.colorScheme.text.primary,
            maxLines = 1,
        )
    }
}

@Composable
fun ActivityHeaderCell(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        MoonTextShimmer(
            text = "Yesterday",
            style = UIKit.typography.label1,
        )
    }
}