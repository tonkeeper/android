package com.tonapps.tonkeeper.ui.screen.events.compose.history.state

import androidx.compose.runtime.Immutable
import com.tonapps.tonkeeper.ui.screen.events.compose.history.TxEventsAction

@Immutable
data class TxContentState(
    val screenState: TxScreenState,
    val selectedFilterId: Int,
    val hiddenBalances: Boolean,
    val onDispatch: (TxEventsAction) -> Unit
)