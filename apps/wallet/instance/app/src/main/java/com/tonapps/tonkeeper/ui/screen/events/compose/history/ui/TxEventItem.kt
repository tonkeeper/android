package com.tonapps.tonkeeper.ui.screen.events.compose.history.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import com.tonapps.tonkeeper.ui.screen.events.compose.history.TxEventsAction
import ui.components.events.EventHeader
import ui.components.events.EventItem
import ui.components.events.UiEvent

@Composable
@NonRestartableComposable
fun TxEventItem(
    item: UiEvent,
    hiddenBalances: Boolean,
    dispatch: (action: TxEventsAction) -> Unit
) {
    when (item) {
        is UiEvent.Item -> {
            EventItem(
                event = item,
                hiddenBalances = hiddenBalances,
                onClick = { id, part ->
                    dispatch(TxEventsAction.Details(id, part))
                }
            )
        }
        is UiEvent.Header -> EventHeader(item.title)
    }
}