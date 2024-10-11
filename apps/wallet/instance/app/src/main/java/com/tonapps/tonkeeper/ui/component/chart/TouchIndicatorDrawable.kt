package com.tonapps.tonkeeper.ui.component.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.withTranslation
import uikit.extensions.dp
import uikit.extensions.withAlpha

class TouchIndicatorDrawable(context: Context): BaseChartDrawable(context) {

    private var touchX = -1f
    private var indicatorY = -1f

    private val touchIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        style = Paint.Style.FILL
        strokeWidth = strokeSize / 2f
    }

    private val touchIndicatorShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor.withAlpha(.2f)
    }

    fun setData(touchX: Float, indicatorY: Float) {
        this.touchX = touchX
        this.indicatorY = indicatorY
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        if (touchX < 0 || indicatorY < 0) {
            return
        }

        canvas.withTranslation(bounds.left.toFloat(), bounds.top + strokeSize) {
            drawLine(touchX, 0f, touchX, chartHeight, touchIndicatorPaint)
            drawCircle(touchX, indicatorY, 6f.dp, touchIndicatorPaint)
            drawCircle(touchX, indicatorY, 16f.dp, touchIndicatorShadowPaint)
        }
    }

}