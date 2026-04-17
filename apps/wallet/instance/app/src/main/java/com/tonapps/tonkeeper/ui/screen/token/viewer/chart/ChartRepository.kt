package com.tonapps.tonkeeper.ui.screen.token.viewer.chart

import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.ChartEntity
import com.tonapps.wallet.data.settings.ChartPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChartRepository(private val api: API) {

    suspend fun loadChart(
        tokenAddress: String,
        currency: String,
        period: ChartPeriod,
        isStable: Boolean,
    ): List<ChartEntity> = withContext(Dispatchers.IO) {
        if (isStable) {
            return@withContext listOf(ChartEntity.EMPTY)
        }
        val endDate = System.currentTimeMillis() / 1000
        val startDate = endDate - period.durationSeconds
        val chart = api.loadChart(tokenAddress, currency, startDate, endDate)
        chart.ifEmpty { listOf(ChartEntity.EMPTY) }
    }
}
