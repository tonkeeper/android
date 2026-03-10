package uikit.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import com.tonapps.uikit.color.separatorCommonColor
import uikit.base.BaseDrawable
import uikit.extensions.dp
import kotlin.math.roundToInt

abstract class BarDrawable(private val context: Context): BaseDrawable() {

    interface BarDrawableOwner {
        fun setDivider(value: Boolean)
    }

    private class Divider(private val parent: BarDrawable) : Runnable {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = parent.context.separatorCommonColor
            strokeWidth = 1f.dp
        }

        private var baseAlpha = Color.alpha(paint.color)
        private val duration = 69L
        private val frameDelay = 16L
        private var alpha = 0f
        private var from = 0f
        private var to = 0f
        private var startAt = 0L
        private var targetVisible = false

        override fun run() {
            val now = SystemClock.uptimeMillis()
            val t = ((now - startAt).toFloat() / duration).coerceIn(0f, 1f)
            val eased = t * t * (3f - 2f * t)
            alpha = from + (to - from) * eased

            parent.invalidateSelf()

            if (t < 1f) {
                parent.scheduleSelf(this, now + frameDelay)
            } else {
                alpha = to
            }
        }

        fun toggle(visible: Boolean) {
            if (targetVisible == visible && ((visible && alpha >= 1f) || (!visible && alpha <= 0f))) {
                return
            }
            targetVisible = visible
            from = alpha
            to = if (visible) 1f else 0f
            startAt = SystemClock.uptimeMillis()
            parent.unscheduleSelf(this)
            parent.scheduleSelf(this, startAt)
        }

        fun draw(canvas: Canvas, startX: Float, startY: Float, stopX: Float, stopY: Float) {
            if (alpha <= 0f) return
            paint.alpha = (baseAlpha * alpha).roundToInt().coerceIn(0, 255)
            canvas.drawLine(startX, startY, stopX, stopY, paint)
        }
    }

    private val divider = Divider(this)

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.TRANSPARENT
    }

    abstract val y: Float

    override fun draw(canvas: Canvas) {
        if (backgroundPaint.color != Color.TRANSPARENT) {
            canvas.drawPaint(backgroundPaint)
        }

        divider.draw(canvas, 0f, y, bounds.right.toFloat(), y)
    }

    fun setColor(color: Int) {
        if (backgroundPaint.color != color) {
            backgroundPaint.color = color
            invalidateSelf()
        }
    }

    fun setDivider(value: Boolean) {
        divider.toggle(value)
    }
}