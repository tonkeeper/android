package com.tonapps.tonkeeper.ui.component.coin.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.tonapps.uikit.color.textSecondaryColor
import uikit.base.BaseDrawable
import uikit.extensions.dp

class SuffixDrawable(
    context: Context,
    private val textPaint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f.dp
        typeface = ResourcesCompat.getFont(context, uikit.R.font.montserrat_semi_bold)
        textAlign = Paint.Align.LEFT
        color = context.textSecondaryColor
    }
) : BaseDrawable() {

    private var textWidth = 0f
    private var textHeight = 0f
    private val textBounds = Rect()

    var textSize: Float
        get() = textPaint.textSize
        set(value) {
            textPaint.textSize = value
            calculateTextSize(text ?: return)
            invalidateSelf()
        }

    var text: String? = null
        set(value) {
            field = value
            if (value == null) {
                textHeight = 0f
                textWidth = 0f
            } else {
                calculateTextSize(value)
            }
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) {
        val string = text ?: return
        val centerY = bounds.height() / 2f - textBounds.centerY()
        canvas.drawText(string, 0f, centerY, textPaint)
    }

    private fun calculateTextSize(text: String) {
        textPaint.getTextBounds(text, 0, text.length, textBounds)
        textHeight = textBounds.height().toFloat()
        textWidth = textPaint.measureText(text)
    }

    override fun getOpacity() = PixelFormat.TRANSLUCENT

    override fun getIntrinsicHeight() = textHeight.toInt()

    override fun getIntrinsicWidth() = textWidth.toInt()
}