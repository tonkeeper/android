package uikit.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Shader
import com.tonapps.uikit.color.backgroundContentTintColor
import uikit.extensions.withAlpha

class LoadingDrawable(context: Context) : BaseChartDrawable(context) {

    private val color = context.backgroundContentTintColor
    private val linePath = Path()
    private val fillPath = Path()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = this@LoadingDrawable.color
        strokeWidth = strokeSize
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(linePath, linePaint)
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        fillPaint.shader = LinearGradient(
            0f, 0f, 0f, chartHeight,
            color.withAlpha(GRADIENT_ALPHA), Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        rebuildPaths()
    }

    private fun rebuildPaths() {
        linePath.reset()
        fillPath.reset()

        val width = chartWidth
        val height = chartHeight
        if (width <= 0f || height <= 0f) return

        val xScale = width / SVG_VIEW_WIDTH
        val yScale = height / SVG_VIEW_HEIGHT
        val minY = strokeSize / 2f
        val maxY = height - minY
        val baseline = height + strokeSize * 2

        SPARKLINE_POINTS.forEachIndexed { index, (px, py) ->
            val x = px * xScale
            val y = (py * yScale).coerceIn(minY, maxY)
            if (index == 0) {
                linePath.moveTo(x, y)
                fillPath.moveTo(x, baseline)
                fillPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }

        fillPath.lineTo(SPARKLINE_POINTS.last().first * xScale, baseline)
        fillPath.close()
    }

    private companion object {
        private const val SVG_VIEW_WIDTH = 390f
        private const val SVG_VIEW_HEIGHT = 167f
        private const val GRADIENT_ALPHA = 76

        private val SPARKLINE_POINTS = listOf(
            390f to 41.5f, 380f to 41.5f, 372f to 17.5f, 362f to 21.5f,
            350f to 9.5f, 340f to 15.5f, 328f to 14.5f, 316f to 24.5f,
            304f to 6.5f, 292f to 56.5f, 280f to 58.5f, 268f to 72.5f,
            255.5f to 1f, 244f to 19.5f, 232f to 26.5f, 218f to 6.5f,
            207f to 18.5f, 196f to 16.5f, 184f to 39.5f, 172f to 37.5f,
            160f to 57.5f, 148f to 64.5f, 136f to 85.5f, 124f to 72.5f,
            112f to 76.5f, 100f to 96.5f, 88f to 142.5f, 76f to 146.5f,
            64f to 131.5f, 52f to 165.5f, 40f to 151.5f, 28f to 154.5f,
            16f to 125.5f, 4f to 145.5f, 0f to 145.5f,
        )
    }
}
