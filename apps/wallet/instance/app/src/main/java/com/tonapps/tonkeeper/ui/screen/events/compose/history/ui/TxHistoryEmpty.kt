package com.tonapps.tonkeeper.ui.screen.events.compose.history.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.tonapps.tonkeeper.ui.screen.events.compose.history.TxEventsAction
import com.tonapps.tonkeeper.ui.screen.events.compose.history.TxEventsViewModel
import com.tonapps.wallet.localization.Localization
import ui.components.moon.cell.MoonEmptyCell
import ui.theme.Dimens

@Composable
fun TxHistoryEmpty(
    viewModel: TxEventsViewModel,
    innerPadding: PaddingValues,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = Dimens.offsetLarge),
        contentAlignment = Alignment.Center,
    ) {
        MoonEmptyCell(
            title = stringResource(Localization.empty_history_title),
            subtitle = stringResource(Localization.empty_history_subtitle),
            firstButtonText = stringResource(Localization.buy_toncoin),
            onFirstClick = { viewModel.dispatch(TxEventsAction.BuyTon) },
            secondButtonText = stringResource(Localization.receive),
            onSecondClick = { viewModel.dispatch(TxEventsAction.OpenQR) },
        )
    }
}
