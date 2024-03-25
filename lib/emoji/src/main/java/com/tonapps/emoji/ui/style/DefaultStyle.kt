package com.tonapps.emoji.ui.style

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import com.tonapps.emoji.compat.EmojiCompatHelper

open class DefaultStyle(typeface: Typeface = Typeface.DEFAULT) {

    constructor(context: Context, @FontRes id: Int) : this(ResourcesCompat.getFont(context, id)!!)

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val size: Int

    init {
        textPaint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 30f, Resources.getSystem().displayMetrics)
        textPaint.color = Color.BLACK
        textPaint.typeface = typeface

        size = run {
            val metrics = textPaint.fontMetricsInt
            metrics.bottom - metrics.top
        }
    }

    fun measureText(emoji: CharSequence): Float = textPaint.measureText(emoji, 0, emoji.length)

    fun newLayoutBuilder(emoji: CharSequence, width: Int): StaticLayout.Builder {
        return StaticLayout.Builder.obtain(emoji, 0, emoji.length, textPaint, width)
    }

    fun draw(emoji: CharSequence): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas, emoji)
        return bitmap
    }

    open fun draw(canvas: Canvas, value: CharSequence) {
        val emoji = EmojiCompatHelper.process(value)
        val textWidth = measureText(emoji)
        val width = canvas.width
        val scale = width / textWidth
        canvas.scale(scale, scale)
        val builder = newLayoutBuilder(emoji, width)
        builder.setAlignment(Layout.Alignment.ALIGN_NORMAL)
        builder.setMaxLines(1)
        builder.setLineSpacing(0f, 1f)
        builder.setIncludePad(false)
        builder.build().draw(canvas)
    }
}