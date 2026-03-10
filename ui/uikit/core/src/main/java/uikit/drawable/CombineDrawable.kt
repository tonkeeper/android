package uikit.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.graphics.toRect
import uikit.base.BaseDrawable
import uikit.extensions.asCircle
import uikit.extensions.dp
import uikit.extensions.drawable

class CombineDrawable(
    private val context: Context,
    private val size: Int = defaultSize,
    private val borderColor: Int,
    private val drawables: List<Drawable>
): BaseDrawable() {

    companion object {
        private val defaultSize = 18.dp
    }

    private val margin: Int = size / drawables.count()
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f.dp
        color = borderColor
    }
    private val hasBorder = borderColor != Color.TRANSPARENT

    constructor(
        context: Context,
        size: Int = defaultSize,
        borderColor: Int,
        icons: IntArray
    ) : this(
        context = context,
        size = size,
        borderColor = borderColor,
        drawables = icons.map { context.drawable(it).asCircle() }
    )

    override fun draw(canvas: Canvas) {
        if (drawables.isEmpty()) {
            return
        }

        val top = bounds.height() / 2f - size / 2f
        var startX = (bounds.right - size).toFloat()

        for (drawable in drawables) {
            val rect = RectF(startX, top, startX + size, top + size)
            drawable.bounds = rect.toRect()
            drawable.draw(canvas)
            if (hasBorder) {
                canvas.drawCircle(
                    rect.centerX(),
                    rect.centerY(),
                    size / 2f,
                    borderPaint
                )
            }
            startX -= margin
        }
    }

}