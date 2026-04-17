package com.tonapps.tonkeeper.ui.screen.events.compose.history.ui.placeholder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.theme.Dimens
import ui.theme.modifiers.rememberShimmerPhase

@Composable
fun TxEventsPlaceholder(innerPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(innerPadding),
    ) {
        val shimmerPhase by rememberShimmerPhase()
        val shimmerPhaseProvider = { shimmerPhase }

        TxFiltersPlaceholder(shimmerPhaseProvider = shimmerPhaseProvider)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.offsetMedium),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TxEventItemPlaceholder(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                height = 32.dp,
                shimmerPhaseProvider = shimmerPhaseProvider,
            )
            for (i in 1..10) {
                val height = remember { (78..98).random().dp }
                TxEventItemPlaceholder(height = height, shimmerPhaseProvider = shimmerPhaseProvider)
            }
        }
    }
}
