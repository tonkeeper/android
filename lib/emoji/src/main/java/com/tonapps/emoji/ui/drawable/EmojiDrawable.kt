package com.tonapps.emoji.ui.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.animation.DecelerateInterpolator
import com.tonapps.emoji.Emoji
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class EmojiDrawable(
    private val context: Context
): Drawable() {

    private companion object {
        private const val ALPHA = 255
        private const val FADE_DURATION = 125L
        private val interpolator = DecelerateInterpolator()
    }

    private val placeholderDrawable = PlaceholderDrawable()
    private var bitmapDrawable: PictogramDrawable? = null

    private var currentJob: Job? = null
    private var currentEmoji: CharSequence = ""
    private var startTime = 0L

    var fadeEnable: Boolean = false

    override fun draw(canvas: Canvas) {
        bitmapDrawable?.bounds = bounds
        if (fadeEnable) {
            drawFade(canvas)
        } else {
            drawDefault(canvas)
        }
    }

    private fun drawDefault(canvas: Canvas) {
        if (bitmapDrawable == null) {
            placeholderDrawable.draw(canvas)
            return
        }
        val drawable = bitmapDrawable ?: return
        if (!fadeEnable && drawable.alpha != ALPHA) {
            drawable.alpha = ALPHA
        }
        drawable.draw(canvas)
    }

    private fun drawFade(canvas: Canvas) {
        val bitmapAlpha = bitmapDrawable?.alpha ?: 0
        if (bitmapAlpha != ALPHA) {
            placeholderDrawable.draw(canvas)
        }

        val drawable = bitmapDrawable ?: return

        if (bitmapAlpha == ALPHA) {
            drawable.draw(canvas)
            return
        }

        if (startTime == 0L) {
            startTime = SystemClock.uptimeMillis()
        }

        val progress = getProgress()
        val newAlpha = (ALPHA * progress).toInt()

        drawable.alpha = newAlpha
        drawable.draw(canvas)

        placeholderDrawable.alpha = ALPHA - newAlpha

        invalidateSelf()
    }

    private fun getProgress(): Float {
        var value = (SystemClock.uptimeMillis() - startTime) / FADE_DURATION.toFloat()
        value = if (value > 1) 1f else value
        return interpolator.getInterpolation(value)
    }

    fun setEmoji(emoji: CharSequence) {
        if (currentEmoji == emoji) {
            return
        }

        currentEmoji = emoji
        clear()
        load()
    }

    private fun load() {
        currentJob = Emoji.scope.launch(Dispatchers.Default) {
            val drawable = Emoji.getDrawable(context, currentEmoji)
            setEmojiDrawable(drawable)
        }
    }

    private fun clear() {
        currentJob?.cancel()
        currentJob = null

        bitmapDrawable = null
        invalidateSelf()
    }

    private suspend fun setEmojiDrawable(
        drawable: BitmapDrawable
    ) = withContext(Dispatchers.Main) {
        drawable as PictogramDrawable
        if (drawable.emoji == currentEmoji) {
            drawable.bounds = bounds
            bitmapDrawable = drawable
            startTime = 0L
            invalidateSelf()
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        placeholderDrawable.bounds = bounds
        bitmapDrawable?.bounds = bounds
    }

    override fun setAlpha(alpha: Int) { }

    override fun getAlpha() = 0

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    override fun getOpacity() = PixelFormat.TRANSLUCENT

}