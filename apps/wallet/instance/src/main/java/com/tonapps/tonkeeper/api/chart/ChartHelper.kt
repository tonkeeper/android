package com.tonapps.tonkeeper.api.chart

import com.tonapps.tonkeeper.api.internal.Tonkeeper
import com.tonapps.tonkeeper.api.withRetry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.tonapps.network.Network
import org.json.JSONObject

object ChartHelper {

    private const val BASE_URL = "https://${Tonkeeper.HOST}/stock/chart-new"

    suspend fun getEntity(period: ChartPeriod): List<ChartEntity> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ChartEntity>()
        val url = "$BASE_URL?period=${period.value}"
        val response = withRetry { Network.get(url) } ?: return@withContext result
        try {
            val data = JSONObject(response).getJSONArray("data")
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                result.add(ChartEntity(item))
            }
            result
        } catch (e: Throwable) {
            result
        }
    }

}