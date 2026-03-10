package com.tonapps.tonkeeper.ui.screen.events.compose.history.state

import androidx.compose.runtime.Immutable

@Immutable
data class TxScreenUiState(
    val isLoading: Boolean = true,
    val hiddenBalances: Boolean = false,
)