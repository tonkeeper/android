package uikit.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import androidx.core.graphics.withTranslation
import com.tonapps.uikit.color.textPrimaryColor
import uikit.extensions.dp
import uikit.extensions.withAlpha

class TouchIndicatorDrawable(context: Context) : BaseChartDrawable(context) {

    private var touchX = -1f
    private var indicatorY = -1f

    private val touchIndicatorLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.textPrimaryColor
        style = Paint.Style.STROKE
        strokeWidth = 1f.dp
        pathEffect = DashPathEffect(floatArrayOf(4f.dp, 4f.dp), 0f)
    }

    private val touchIndicatorDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.textPrimaryColor
        style = Paint.Style.FILL
    }

    private val touchIndicatorShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.textPrimaryColor.withAlpha(.2f)
    }

    fun setData(touchX: Float, indicatorY: Float) {
        this.touchX = touchX
        this.indicatorY = indicatorY
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        if (touchX < 0 || indicatorY < 0) return
        canvas.withTranslation(bounds.left.toFloat(), bounds.top + strokeSize) {
            drawLine(touchX, 0f, touchX, chartHeight, touchIndicatorLinePaint)
            drawCircle(touchX, indicatorY, 6f.dp, touchIndicatorDotPaint)
            drawCircle(touchX, indicatorY, 16f.dp, touchIndicatorShadowPaint)
        }
    }
}
