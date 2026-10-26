package com.tonapps.tonkeeper.ui.screen.staking

data class StakingInsufficientFundsUiState(
    val requiredFormat: CharSequence,
    val balanceFormat: CharSequence,
    val reserveHint: String?,
)
