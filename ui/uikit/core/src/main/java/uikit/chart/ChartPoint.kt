package uikit.chart

data class ChartPoint(
    val date: Long,
    val price: Float,
) {
    val isEmpty: Boolean get() = this == EMPTY

    companion object {
        val EMPTY = ChartPoint(0, 0f)
    }
}
