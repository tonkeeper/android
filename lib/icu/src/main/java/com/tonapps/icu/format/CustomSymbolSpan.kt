package com.tonapps.icu.format

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.style.ImageSpan

internal open class CustomSymbolSpan(
    private val iconMedium: Drawable,
    private val iconBold: Drawable,
): ImageSpan(ColorDrawable(Color.TRANSPARENT), ALIGN_BOTTOM) {

    constructor(context: Context, mediumResId: Int, boldResId: Int) : this(
        context.getDrawable(mediumResId)!!,
        context.getDrawable(boldResId)!!
    )

    override fun getSize(
        paint: Paint,
        text: CharSequence?,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        val drawable = createDrawable(paint) ?: return 0

        val bounds = drawable.bounds
        fm?.let { metrics ->
            val fontHeight = metrics.descent - metrics.ascent
            val drawableHeight = bounds.height()

            val centerY = metrics.ascent + fontHeight / 2

            metrics.ascent = centerY - drawableHeight / 2
            metrics.top = metrics.ascent
            metrics.descent = centerY + drawableHeight / 2
            metrics.bottom = metrics.descent
        }
        return bounds.right
    }

    private fun createDrawable(paint: Paint): Drawable? {
        if (paint is TextPaint) {
            return createDrawable(paint)
        }
        return null
    }

    private fun createDrawable(textPaint: TextPaint): Drawable {
        val isBold = textPaint.isFakeBoldText || textPaint.typeface.isBold
        val selectedDrawable = if (isBold) iconBold else iconMedium
        selectedDrawable.setTint(textPaint.color)
        setSize(selectedDrawable, textPaint.textSize)
        return selectedDrawable
    }

    open fun setSize(selectedDrawable: Drawable, size: Float) {
        val iconWidth = selectedDrawable.intrinsicWidth.toFloat()
        val iconHeight = selectedDrawable.intrinsicHeight.toFloat()
        val aspectRatio = iconWidth / iconHeight
        val width = (size * aspectRatio).toInt()
        selectedDrawable.setBounds(0, 0, width, size.toInt())
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
        val drawable = createDrawable(paint) ?: return

        canvas.save()

        val fm = paint.fontMetricsInt
        val drawableHeight = drawable.bounds.height()
        val fontHeight = fm.descent - fm.ascent
        val centerY = y + fm.ascent + fontHeight / 2
        val transY = centerY - drawableHeight / 2
        canvas.translate(x, transY * 1.2f)
        drawable.draw(canvas)

        canvas.restore()
    }

}