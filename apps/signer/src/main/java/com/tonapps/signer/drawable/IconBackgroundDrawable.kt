package com.tonapps.signer.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.toRectF
import com.tonapps.uikit.color.backgroundContentColor
import com.tonapps.uikit.color.separatorCommonColor
import uikit.base.BaseDrawable
import uikit.extensions.dp

class IconBackgroundDrawable(
    context: Context,
    private val radius: Float
): BaseDrawable() {

    private val borderSize = 1f.dp
    private val borderSizeHalf = borderSize / 2f

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.backgroundContentColor
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.separatorCommonColor
        strokeWidth = borderSize
        style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
        val rect = bounds.toRectF()
        canvas.drawRoundRect(rect, radius, radius, backgroundPaint)
        canvas.drawRoundRect(
            rect.left + borderSizeHalf,
            rect.top + borderSizeHalf,
            rect.right - borderSizeHalf,
            rect.bottom - borderSizeHalf,
            radius,
            radius,
            borderPaint
        )
    }

}