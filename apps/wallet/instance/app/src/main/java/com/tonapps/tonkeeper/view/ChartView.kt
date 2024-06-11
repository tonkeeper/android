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
import androidx.core.view.doOnLayout
import com.tonapps.uikit.color.accentBlueColor
import com.tonapps.wallet.api.entity.ChartEntity
import uikit.extensions.dp
import uikit.extensions.withAlpha

class ChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private var data = listOf(ChartEntity(0, 0f))
    private var square = false

    fun setData(data: List<ChartEntity>, square: Boolean) {
        this.data = data
        this.square = square
        doOnLayout { buildPath() }
    }

    private val accentColor = context.accentBlueColor

    private val path = Path()

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        strokeWidth = 2f.dp
        style = Paint.Style.STROKE
    }

    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        Log.d("ChartViewLog", "draw chart")
        val lineWidthPart = linePaint.strokeWidth / 2f
        canvas.translate(-lineWidthPart, lineWidthPart)
        canvas.drawPath(path, linePaint)
        canvas.drawPath(path, gradientPaint)
    }

    private fun buildPath() {
        path.reset()

        if (data.isEmpty()) {
            return
        }

        val maxPrice = data.maxOf { it.price }

        val viewWidth = width - 2 * linePaint.strokeWidth
        val viewHeight = height - 2 * linePaint.strokeWidth
        val stepWidth = viewWidth / data.size
        val points = mutableListOf<PointF>()

        for ((index, item) in data.withIndex()) {
            val x = index * stepWidth
            val y = viewHeight - (item.price / maxPrice) * viewHeight
            points.add(PointF(x, y))
        }

        gradientPaint.shader = LinearGradient(
            0f, 0f, 0f, viewHeight,
            accentColor.withAlpha(.3f), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )

        if (square) {
            buildSquarePath(points, viewHeight)
        } else {
            buildDefaultPath(points, viewHeight)
        }
        invalidate()
    }

    private fun buildDefaultPath(points: MutableList<PointF>, viewHeight: Float) {
        val firstPoint = points.removeFirst()
        val lastPoint = points.removeLast()

        path.moveTo(firstPoint.x - linePaint.strokeWidth, firstPoint.y)
        for (point in points) {
            path.lineTo(point.x, point.y)
        }

        path.lineTo(lastPoint.x + (linePaint.strokeWidth * 2f), lastPoint.y)
        path.lineTo(lastPoint.x + (linePaint.strokeWidth * 12f.dp), viewHeight + (linePaint.strokeWidth * 2))
        path.lineTo(0f, viewHeight + 12f.dp)
        path.close()
    }

    private fun buildSquarePath(points: MutableList<PointF>, viewHeight: Float) {
        val firstPoint = points.first()
        val lastPoint = points.last()
        path.moveTo(firstPoint.x - linePaint.strokeWidth, firstPoint.y)
        for ((index, point) in points.withIndex()) {
            if (index < points.size - 1) {
                val nextPoint = points[index + 1]
                path.lineTo(nextPoint.x, point.y)
                path.lineTo(nextPoint.x, nextPoint.y)
            } else {
                path.lineTo(point.x, point.y)
            }
        }

        path.lineTo(lastPoint.x + (linePaint.strokeWidth * 2f), lastPoint.y)
        path.lineTo(lastPoint.x + (linePaint.strokeWidth * 12f.dp), viewHeight + (linePaint.strokeWidth * 2))
        path.lineTo(0f, viewHeight + (linePaint.strokeWidth * 2))
        path.close()
    }
}