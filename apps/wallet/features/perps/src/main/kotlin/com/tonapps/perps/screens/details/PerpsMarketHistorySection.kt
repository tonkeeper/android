package com.tonapps.perps.screens.details

import androidx.compose.runtime.Composable
import com.tonapps.perps.data.PerpMarket

data class PerpsMarketHistoryRequest(
    val walletId: String,
    val marketIndex: Int,
    val attempt: Int,
)

interface PerpsMarketHistorySection {
    @Composable
    fun Content(
        request: PerpsMarketHistoryRequest,
        market: PerpMarket,
        onSeeAll: () -> Unit,
    )
}
