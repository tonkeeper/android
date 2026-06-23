package uikit.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Shader
import androidx.core.graphics.withTranslation
import uikit.extensions.withAlpha
import kotlin.math.max

class ChartDrawable(context: Context) : BaseChartDrawable(context) {

    private var data = listOf<ChartPoint>()
    private val linePath = Path()
    private val fillPath = Path()

    val entities: List<ChartPoint>
        get() = data.toList()

    val stepX: Float
        get() = if (data.isEmpty()) 0f else chartWidth / data.size

    val isEmpty: Boolean
        get() = data.isEmpty()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        strokeWidth = strokeSize
        style = Paint.Style.STROKE
    }

    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.FILL
    }

    fun setData(data: List<ChartPoint>, isSquare: Boolean) {
        this.data = data
        linePath.reset()
        fillPath.reset()
        invalidateSelf()
    }

    fun getDotY(point: ChartPoint): Float {
        val minPrice = data.minOfOrNull { it.price } ?: return chartHeight / 2f
        val maxPrice = data.maxOfOrNull { it.price } ?: return chartHeight / 2f
        val range = maxPrice - minPrice
        if (range == 0f) return chartHeight / 2f

        val y = chartHeight - ((point.price - minPrice) / range) * chartHeight
        if (y.isNaN() || y.isInfinite()) return chartHeight / 2f
        return y
    }

    override fun draw(canvas: Canvas) {
        if (data.isEmpty()) return
        if (linePath.isEmpty || fillPath.isEmpty) buildPaths()

        canvas.withTranslation(bounds.left.toFloat(), bounds.top + strokeSize) {
            drawPath(fillPath, gradientPaint)
            drawPath(linePath, linePaint)
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        gradientPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            chartHeight,
            accentColor.withAlpha(76),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        linePath.reset()
        fillPath.reset()
    }

    private fun buildPaths() {
        linePath.reset()
        fillPath.reset()

        var maxPrice = data.maxOf { it.price }
        var minPrice = data.minOf { it.price }
        if (maxPrice == minPrice) {
            maxPrice += 1f
            minPrice = 0f
        }

        val priceRange = max(maxPrice - minPrice, Float.MIN_VALUE)
        val points = data.mapIndexed { index, point ->
            PointF(
                index * stepX,
                chartHeight - ((point.price - minPrice) / priceRange) * chartHeight
            )
        }

        if (points.isEmpty()) return

        val first = points.first()
        linePath.moveTo(first.x, first.y)
        fillPath.moveTo(first.x, first.y)

        for (i in 1 until points.size) {
            val point = points[i]
            linePath.lineTo(point.x, point.y)
            fillPath.lineTo(point.x, point.y)
        }

        val last = points.last()
        fillPath.lineTo(last.x, chartHeight + strokeSize * 2)
        fillPath.lineTo(first.x, chartHeight + strokeSize * 2)
        fillPath.close()
    }
}
