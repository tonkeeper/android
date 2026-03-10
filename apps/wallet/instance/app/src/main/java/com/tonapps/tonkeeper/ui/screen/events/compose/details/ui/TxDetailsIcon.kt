package com.tonapps.tonkeeper.ui.screen.events.compose.details.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tonapps.tonkeeper.ui.screen.events.compose.details.state.UiState
import ui.components.image.TokenImage
import ui.components.image.TokenImageBorder

@Composable
fun TxDetailsIcon(
    icons: List<UiState.Icon>
) {
    if (icons.size > 1) {
        TxDetailsIcons(icons)
    } else if (icons.size == 1) {
        val icon = icons.first()
        TokenImage(
            icon = icon.url,
            subicon = icon.subicon,
            modifier = Modifier.size(96.dp),
            size = 256
        )
    }
}

@Composable
fun TxDetailsIcons(
    icons: List<UiState.Icon>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp),
        horizontalArrangement = Arrangement.spacedBy((-8).dp, Alignment.CenterHorizontally)
    ) {
        icons.forEachIndexed { index, icon ->
            TokenImageBorder(
                modifier = Modifier.size(72.dp),
                icon = icon.url,
                size = 128
            )
        }
    }
}