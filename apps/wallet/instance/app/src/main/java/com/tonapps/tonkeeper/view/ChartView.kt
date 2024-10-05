package com.tonapps.tonkeeper.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Shader
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.graphics.withTranslation
import androidx.core.view.doOnLayout
import com.tonapps.uikit.color.accentBlueColor
import com.tonapps.wallet.api.entity.ChartEntity
import uikit.extensions.dp
import uikit.extensions.withAlpha
import kotlin.math.max

class ChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val strokeSize = 2f.dp

    private val chartWidth: Float
        get() = (width - paddingLeft - paddingRight) + strokeSize * 2

    private val chartHeight: Float
        get() = (height - paddingTop - paddingBottom) - (strokeSize * 2)

    private val accentColor = context.accentBlueColor
    private val path = Path()
    private var data = listOf(ChartEntity(0, 0f))
    private var isSquare = false

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
        if (width > 0 && height > 0) {
            buildPath()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        gradientPaint.shader = LinearGradient(
            0f, 0f, 0f, chartHeight,
            accentColor.withAlpha(76),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )

        buildPath()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.withTranslation(paddingLeft.toFloat(), paddingTop + strokeSize) {
            drawPath(path, linePaint)
            drawPath(path, gradientPaint)
        }
    }

    private fun buildPath() {
        path.reset()
        if (data.isEmpty()) {
            invalidate()
            return
        }

        val maxPrice = data.maxOf { it.price }
        val minPrice = data.minOf { it.price }
        val priceRange = max(maxPrice - minPrice, 0.01f)

        val stepX = chartWidth / data.size
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

        /*if (isSquare) {
            buildSquarePath(points)
        } else {
            buildPath(points)
        }*/

        buildPath(points)

        path.close()

        invalidate()
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