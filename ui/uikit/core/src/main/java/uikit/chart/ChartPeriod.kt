package uikit.chart

enum class ChartPeriod(
    val value: String,
    val title: String,
    val durationSeconds: Long,
) {
    hour("1H", "H", 60L * 60),
    day("1D", "D", 60L * 60 * 24),
    week("7D", "W", 60L * 60 * 24 * 7),
    month("1M", "M", 60L * 60 * 24 * 30),
    halfYear("6M", "6M", 60L * 60 * 24 * 30 * 6),
    year("1Y", "Y", 60L * 60 * 24 * 365);

    companion object {

        fun of(value: String?): ChartPeriod {
            if (value == null) return month
            return entries.firstOrNull { it.value == value } ?: month
        }
    }
}
