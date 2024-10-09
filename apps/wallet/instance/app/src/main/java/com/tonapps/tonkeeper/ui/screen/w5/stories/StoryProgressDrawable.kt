package com.tonapps.tonkeeper.ui.screen.w5.stories

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.tonapps.uikit.color.iconPrimaryColor
import uikit.base.BaseDrawable
import uikit.extensions.dp
import uikit.extensions.withAlpha

class StoryProgressDrawable(context: Context): BaseDrawable() {

    var progress: Float = 0f
        set(value) {
            field = value
            invalidateSelf()
        }

    private val radius = 2f.dp
    private val backgroundColor = context.iconPrimaryColor.withAlpha(.24f)
    private val progressColor = context.iconPrimaryColor

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = backgroundColor
        style = Paint.Style.FILL
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = progressColor
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        val left = bounds.left.toFloat()
        val top = bounds.top.toFloat()
        val right = bounds.right.toFloat()
        val bottom = bounds.bottom.toFloat()

        canvas.drawRoundRect(left, top, right, bottom, radius, radius, backgroundPaint)

        if (progress > 1f) return
        val progressWidth = (right - left) * progress
        canvas.drawRoundRect(left, top, left + progressWidth, bottom, radius, radius, progressPaint)
    }
}