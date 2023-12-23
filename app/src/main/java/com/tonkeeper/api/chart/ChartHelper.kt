package com.tonkeeper.api.chart

import com.tonkeeper.api.withRetry
import core.network.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object ChartHelper {

    private const val BASE_URL = "https://api.tonkeeper.com/stock/chart-new"

    suspend fun getEntity(period: ChartPeriod): List<ChartEntity> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL?period=${period.value}"
        val response = withRetry { Network.get(url) } ?: return@withContext emptyList()
        val data = JSONObject(response).getJSONArray("data")
        val result = mutableListOf<ChartEntity>()
        for (i in 0 until data.length()) {
            val item = data.getJSONObject(i)
            result.add(ChartEntity(item))
        }
        result
    }

}