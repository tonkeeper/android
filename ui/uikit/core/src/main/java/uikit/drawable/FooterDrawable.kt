package uikit.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.tonapps.uikit.color.UIKitColor
import uikit.base.BaseDrawable
import uikit.extensions.dp

class FooterDrawable(context: Context): BaseDrawable() {

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(UIKitColor.separatorCommon)
        strokeWidth = .5f.dp
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.TRANSPARENT
    }

    private var divider: Boolean = false

    override fun draw(canvas: Canvas) {
        if (backgroundPaint.color != Color.TRANSPARENT) {
            canvas.drawPaint(backgroundPaint)
        }

        if (divider) {
            canvas.drawLine(0f, 0f, bounds.right.toFloat(), 0f, dividerPaint)
        }
    }

    fun setDivider(value: Boolean) {
        if (divider != value) {
            divider = value
            invalidateSelf()
        }
    }

    fun setColor(color: Int) {
        if (backgroundPaint.color != color) {
            backgroundPaint.color = color
            invalidateSelf()
        }
    }

}