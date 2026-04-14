package com.tonapps.tonkeeper.ui.screen.events.compose.history.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.tonapps.wallet.localization.Localization
import ui.components.moon.MoonAccentButton
import ui.components.events.UiEvent
import ui.theme.Dimens
import ui.theme.UIKit

@Composable
internal fun TxEventsError(
    items: LazyPagingItems<UiEvent>,
    innerPadding: PaddingValues,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.offsetLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(Localization.unknown_error),
                style = UIKit.typography.h2,
                color = UIKit.colorScheme.text.secondary,
            )

            MoonAccentButton(
                text = stringResource(Localization.retry),
                onClick = { items.refresh() },
            )
        }
    }
}
