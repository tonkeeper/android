package com.tonapps.tonkeeper.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.view.doOnLayout
import com.tonapps.tonkeeper.api.chart.ChartEntity
import com.tonapps.uikit.color.accentBlueColor
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
        doOnLayout {
            buildPath()
            invalidate()
        }
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

        val minX = data.minOf { it.x }.toDouble()
        val maxX = data.maxOf { it.x }.toDouble()
        val minY = data.minOf { it.y }
        val maxY = data.maxOf { it.y }

        val viewWidth = width + linePaint.strokeWidth
        val viewHeight = height + linePaint.strokeWidth

        val widthScale = viewWidth / (maxX - minX)
        val heightScale = viewHeight / (maxY - minY)

        val scaledData = data.map { point ->
            ((point.x - minX) * widthScale).toFloat() to viewHeight - ((point.y - minY) * heightScale)
        }

        path.moveTo(scaledData.first().first, scaledData.first().second)

        for (i in 0 until scaledData.size - 1) {
            val currentPoint = scaledData[i]
            if (square) {
                val nextPoint = scaledData[i + 1]
                path.lineTo(nextPoint.first, currentPoint.second)
                path.lineTo(nextPoint.first, nextPoint.second)
            } else {
                path.lineTo(currentPoint.first, currentPoint.second)
            }
        }

        path.lineTo(scaledData.last().first, viewHeight)
        path.lineTo(0f, viewHeight)
        path.close()

        gradientPaint.shader = LinearGradient(
            0f, 0f, 0f, viewHeight,
            accentColor.withAlpha(.3f), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
    }
}