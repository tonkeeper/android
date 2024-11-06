package com.tonapps.tonkeeper.ui.component.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.Shader
import android.util.Log
import androidx.core.graphics.withTranslation
import com.tonapps.wallet.api.entity.ChartEntity
import uikit.extensions.withAlpha
import kotlin.math.max

class ChartDrawable(context: Context): BaseChartDrawable(context) {

    private var data = listOf<ChartEntity>()
    private val path = Path()
    private var isSquare = false

    val entities: List<ChartEntity>
        get() = data.toList()

    val stepX: Float
        get() = chartWidth / data.size

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

    fun setData(data: List<ChartEntity>, isSquare: Boolean) {
        this.data = data
        this.isSquare = isSquare

        path.reset()
    }

    fun getDotY(entity: ChartEntity): Float {
        val y = chartHeight - ((entity.price - data.minOf { it.price }) / (data.maxOf { it.price } - data.minOf { it.price })) * chartHeight
        if (y.isNaN() || y.isInfinite()) {
            return chartHeight / 2f
        }
        return y
    }

    override fun draw(canvas: Canvas) {
        if (data.isEmpty()) {
            return
        }

        if (path.isEmpty) {
            buildPath()
        }

        canvas.withTranslation(bounds.left.toFloat(), bounds.top + strokeSize) {
            drawPath(path, linePaint)
            drawPath(path, gradientPaint)
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        gradientPaint.shader = LinearGradient(
            0f, 0f, 0f, chartHeight,
            accentColor.withAlpha(76),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
    }

    private fun buildPath() {
        var maxPrice = data.maxOf { it.price }
        var minPrice = data.minOf { it.price }

        if (maxPrice == minPrice) {
            maxPrice += 1f
            minPrice = 0f
        }

        val priceRange = max(maxPrice - minPrice, 0.000000000f)

        val points = mutableListOf<PointF>()
        for ((index, entity) in data.withIndex()) {
            val x = index * stepX
            val y = chartHeight - ((entity.price - minPrice) / priceRange) * chartHeight
            points.add(PointF(x, y))
        }

        points.first().let { point ->
            path.moveTo(point.x - strokeSize, point.y)
            path.lineTo(point.x, point.y)
        }

        buildPath(points)

        /*if (isSquare) {
            buildSquarePath(points)
        } else {
            buildPath(points)
        }*/

        buildPath(points)

        path.close()
    }

    private fun buildPath(points: List<PointF>) {
        for (point in points) {
            path.lineTo(point.x, point.y)
        }
        points.last().let {
            path.lineTo(it.x + strokeSize, it.y + (strokeSize * 2))
            path.lineTo(chartWidth, chartHeight + (strokeSize * 2))
            path.lineTo(-strokeSize, chartHeight + (strokeSize * 2))
        }
    }

    private fun buildSquarePath(points: List<PointF>) {
        for (i in points.indices) {
            val point = points[i]
            if (i < points.size - 1) {
                val nextPoint = points[i + 1]
                path.lineTo(nextPoint.x, point.y)
                path.lineTo(nextPoint.x, nextPoint.y)
            } else {
                path.lineTo(point.x, point.y)
            }
        }
        points.last().let {
            path.lineTo(it.x + strokeSize, it.y)
            path.lineTo(it.x + strokeSize, chartHeight + strokeSize)
            path.lineTo(-strokeSize, chartHeight + strokeSize)
        }
    }
}