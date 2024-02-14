package com.tonapps.tonkeeper.api.chart

enum class ChartPeriod(val value: String) {
    hour("1H"),
    day("1D"),
    week("7D"),
    month("1M"),
    halfYear("6M"),
    year("1Y")
}
