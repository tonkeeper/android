package com.tonapps.wallet.data.settings

enum class ChartPeriod(val value: String) {
    hour("1H"),
    day("1D"),
    week("7D"),
    month("1M"),
    halfYear("6M"),
    year("1Y");

    companion object {

        fun of(value: String?): ChartPeriod {
            if (value == null) return month
            return entries.firstOrNull { it.value == value } ?: month
        }
    }
}
