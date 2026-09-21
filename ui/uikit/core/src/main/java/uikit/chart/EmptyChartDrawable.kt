package uikit.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.text.TextPaint
import androidx.core.graphics.withTranslation
import com.tonapps.uikit.color.iconTertiaryColor
import com.tonapps.uikit.color.textSecondaryColor
import uikit.base.BaseDrawable
import uikit.extensions.dp
import uikit.extensions.setTextAppearance
import kotlin.math.PI
import kotlin.math.sin

class EmptyChartDrawable(context: Context) : BaseDrawable() {

    private companion object {
        private const val WAVE_SAMPLES = 160
        private const val WAVE_PERIODS = 2
    }

    private val lineWidth = 123f.dp
    private val lineHeight = 20f.dp
    private val lineStroke = 4f.dp
    private val textTopMargin = 28f.dp
    private val contentTopMargin = 56f.dp

    var text: String = "No price data is available."
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.iconTertiaryColor
        strokeWidth = lineStroke
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        setTextAppearance(context, uikit.R.style.TextAppearance_Body1)
        color = context.textSecondaryColor
        textAlign = Paint.Align.CENTER
    }

    private val linePath = Path()

    init {
        rebuildPath()
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        rebuildPath()
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return

        val fm = textPaint.fontMetrics
        val top = bounds.top + contentTopMargin
        val lineLeft = bounds.left + (bounds.width() - lineWidth) / 2f

        canvas.withTranslation(lineLeft, top) {
            drawPath(linePath, linePaint)
        }

        val baseline = top + lineHeight + textTopMargin - fm.ascent
        canvas.drawText(text, bounds.exactCenterX(), baseline, textPaint)
    }

    private fun rebuildPath() {
        linePath.reset()

        val inset = lineStroke / 2f
        val usableWidth = lineWidth - inset * 2f
        val amplitude = (lineHeight - lineStroke) / 2f
        val midY = lineHeight / 2f

        for (i in 0..WAVE_SAMPLES) {
            val t = i / WAVE_SAMPLES.toFloat()
            val x = inset + usableWidth * t
            val angle = WAVE_PERIODS * 2.0 * PI * t
            val y = midY - amplitude * sin(angle).toFloat()
            if (i == 0) {
                linePath.moveTo(x, y)
            } else {
                linePath.lineTo(x, y)
            }
        }
    }
}
