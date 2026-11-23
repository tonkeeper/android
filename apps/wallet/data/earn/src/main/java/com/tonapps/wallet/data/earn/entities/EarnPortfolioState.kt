package com.tonapps.wallet.data.earn.entities

sealed interface EarnPortfolioState {

    data class Loaded(
        val portfolio: EarnPortfolioEntity,
        val isStale: Boolean,
    ) : EarnPortfolioState

    data object Unavailable : EarnPortfolioState
}
