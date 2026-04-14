package com.tonapps.wallet.data.settings

enum class ChartPeriod(val value: String, val title: String, val durationSeconds: Long) {
    hour("1H", "H", 3_600L),
    day("1D", "D", 86_400L),
    week("7D", "W", 604_800L),
    month("1M", "M", 2_592_000L),
    halfYear("6M", "6M", 15_552_000L),
    year("1Y", "Y", 31_536_000L);

    companion object {

        fun of(value: String?): ChartPeriod {
            if (value == null) return month
            return entries.firstOrNull { it.value == value } ?: month
        }
    }
}
