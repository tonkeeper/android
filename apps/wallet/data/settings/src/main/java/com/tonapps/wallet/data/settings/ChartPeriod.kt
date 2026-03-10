package com.tonapps.wallet.data.settings

enum class ChartPeriod(val value: String, val title: String) {
    hour("1H", "H"),
    day("1D", "D"),
    week("7D", "W"),
    month("1M", "M"),
    halfYear("6M", "6M"),
    year("1Y", "Y");

    companion object {

        fun of(value: String?): ChartPeriod {
            if (value == null) return month
            return entries.firstOrNull { it.value == value } ?: month
        }
    }
}
