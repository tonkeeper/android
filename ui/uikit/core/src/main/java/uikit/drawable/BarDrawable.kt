package uikit.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.tonapps.uikit.color.separatorCommonColor
import uikit.base.BaseDrawable
import uikit.extensions.dp

abstract class BarDrawable(context: Context): BaseDrawable() {

    interface BarDrawableOwner {
        fun setDivider(value: Boolean)
    }

    private var divider: Boolean = false

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.separatorCommonColor
        strokeWidth = 1f.dp
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.TRANSPARENT
    }

    abstract val y: Float

    override fun draw(canvas: Canvas) {
        if (backgroundPaint.color != Color.TRANSPARENT) {
            canvas.drawPaint(backgroundPaint)
        }

        if (divider) {
            canvas.drawLine(0f, y, bounds.right.toFloat(), y, dividerPaint)
        }
    }

    fun setColor(color: Int) {
        if (backgroundPaint.color != color) {
            backgroundPaint.color = color
            invalidateSelf()
        }
    }

    fun setDivider(value: Boolean) {
        if (divider != value) {
            divider = value
            invalidateSelf()
        }
    }
}