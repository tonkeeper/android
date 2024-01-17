package uikit.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import uikit.R
import uikit.base.BaseDrawable
import uikit.extensions.dp

class HeaderDrawable(context: Context): BaseDrawable() {

    private val color = context.getColor(R.color.backgroundPage)

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.separatorCommon)
        strokeWidth = .5f.dp
    }

    var divider: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    override fun draw(canvas: Canvas) {
        canvas.drawColor(color)
        if (divider) {
            canvas.drawLine(0f, bounds.bottom.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(), dividerPaint)
        }
    }
}