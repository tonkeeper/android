package uikit.span

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.style.ReplacementSpan
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import uikit.extensions.dp
import uikit.extensions.setTextAppearance

class BadgeSpan(
    private val context: Context,
    @ColorInt backgroundColor: Int,
    @ColorInt private val textColor: Int,
    @StyleRes private val textAppearance: Int
): ReplacementSpan() {

    private companion object {
        val cornerRadius = 4f.dp
        val padding = 5f.dp
    }

    private var lastTextPaint: Paint? = null
        set(value) {
            if (field != value) {
                field = value
                updateTextPaint()
            }
        }

    private var textPaint = TextPaint()

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = backgroundColor
        style = Paint.Style.FILL
    }

    private val rectF = RectF()


    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        lastTextPaint = paint
        val textWidth = textPaint.measureText(text, start, end)
        return (textWidth + 2 * padding).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        lastTextPaint = paint

        val textWidth = textPaint.measureText(text, start, end)
        rectF.set(x, top.toFloat(), x + textWidth + 2 * padding, bottom.toFloat())
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, backgroundPaint)

        val textX = x + padding
        val textY = (top + bottom) / 2f - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(text!!, start, end, textX, textY, textPaint)
    }

    private fun updateTextPaint() {
        if (lastTextPaint == null) {
            textPaint = TextPaint()
        } else {
            textPaint = TextPaint(lastTextPaint)
            textPaint.setTextAppearance(context, textAppearance)
            textPaint.color = textColor
        }
    }
}