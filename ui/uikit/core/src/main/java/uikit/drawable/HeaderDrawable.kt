package uikit.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import com.tonapps.uikit.color.separatorCommonColor
import uikit.base.BaseDrawable
import uikit.extensions.dp

class HeaderDrawable(context: Context): BaseDrawable() {

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.separatorCommonColor
        strokeWidth = .5f.dp
    }

    private var divider: Boolean = false

    override fun draw(canvas: Canvas) {
        if (divider) {
            canvas.drawLine(0f, bounds.bottom.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), dividerPaint)
        }
    }

    fun setDivider(value: Boolean) {
        if (divider != value) {
            divider = value
            invalidateSelf()
        }
    }
}