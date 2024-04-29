package uikit.base

import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

abstract class BaseDrawable: Drawable() {

    private var alpha = 255

    fun setAlpha(alpha: Float) = setAlpha((alpha * 255).toInt())

    override fun setAlpha(alpha: Int) {
        this.alpha = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int {
        return alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {

    }

    override fun getOpacity(): Int {
        return PixelFormat.UNKNOWN
    }
}