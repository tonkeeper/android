package com.tonkeeper.api.chart

import core.network.Network
import org.json.JSONObject

class ChartRequest(
    val period: ChartPeriod
) {

    private companion object {
        private const val BASE_URL = "https://api.tonkeeper.com/stock/chart-new"
    }

    fun execute(): List<ChartEntity> {
        val url = "$BASE_URL?period=${period.value}"
        val data = Network.get(url)
        val json = JSONObject(data)

    }

    // https://api.tonkeeper.com/stock/chart-new?period=1D
}