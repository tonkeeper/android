package com.tonapps.emoji.ui.drawable

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable

internal class PictogramDrawable(
    val emoji: CharSequence,
    val context: Context,
    bitmap: Bitmap
): BitmapDrawable(context.resources, bitmap) {

    init {
        setDither(true)
        setAntiAlias(true)
    }

    override fun draw(canvas: Canvas) {
        if (!bitmap.isRecycled) {
            super.draw(canvas)
        }
    }
}