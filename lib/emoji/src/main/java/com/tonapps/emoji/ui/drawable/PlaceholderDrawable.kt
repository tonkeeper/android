package com.tonapps.emoji.ui.drawable

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

internal class PlaceholderDrawable: Drawable() {

    private val paint = Paint().apply {
        color = Color.parseColor("#0Dffffff")
    }

    override fun draw(canvas: Canvas) {
        canvas.drawOval(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), paint)
    }

    override fun setAlpha(alpha: Int) { }

    override fun getAlpha() = 0

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    override fun getOpacity() = PixelFormat.UNKNOWN
}