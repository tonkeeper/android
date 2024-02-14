package com.tonapps.emoji

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.text.Layout
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.util.TypedValue
import com.tonapps.emoji.compat.EmojiCompatHelper

class EmojiDrawable(
    context: Context
): BitmapDrawable(context.resources, createBitmap()) {

    private companion object {

        private val thread = EmojiHelper.createBackgroundPriorityExecutor("EmojiDrawable")

        private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 30f, Resources.getSystem().displayMetrics)
            color = Color.BLACK
        }

        private val size: Int by lazy {
            val metrics = textPaint.fontMetricsInt
            metrics.bottom - metrics.top
        }

        private fun drawEmoji(canvas: Canvas, emoji: CharSequence) {
            val textWidth = textPaint.measureText(emoji, 0, emoji.length)
            val width = canvas.width
            val scale = width / textWidth
            canvas.scale(scale, scale)
            val builder = StaticLayout.Builder.obtain(emoji, 0, emoji.length, textPaint, width)
            builder.setAlignment(Layout.Alignment.ALIGN_NORMAL)
            builder.setMaxLines(1)
            builder.setLineSpacing(0f, 1f)
            builder.setIncludePad(false)
            builder.build().draw(canvas)
        }

        private fun createBitmap() = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    }

    fun setEmoji(emoji: CharSequence) {
        thread.execute {
            bitmap.eraseColor(Color.TRANSPARENT)
            val canvas = Canvas(bitmap)
            drawEmoji(canvas, EmojiCompatHelper.process(emoji))
            EmojiHelper.uiHandler.postAtFrontOfQueue { invalidateSelf() }
        }
    }
}