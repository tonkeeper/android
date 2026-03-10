package com.tonapps.tonkeeper.ui.screen.events.compose.history.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.tonapps.wallet.localization.Localization
import ui.components.bar.top.LargeTopAppBar
import ui.components.button.TKButton
import ui.components.events.UiEvent
import ui.theme.Dimens
import ui.theme.UIKit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TxEventsError(items: LazyPagingItems<UiEvent>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {

        LargeTopAppBar(stringResource(Localization.history))

        Spacer(
            modifier = Modifier.weight(1f)
        )

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
                color = UIKit.colorScheme.text.secondary
            )

            TKButton(
                text = stringResource(Localization.retry),
                onClick = { items.refresh() },
            )
        }

        Spacer(
            modifier = Modifier.weight(1f)
        )

    }
}