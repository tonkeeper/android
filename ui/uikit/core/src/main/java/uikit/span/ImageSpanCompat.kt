package uikit.span

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.style.ImageSpan

class ImageSpanCompat(drawable: Drawable): ImageSpan(drawable, ALIGN_BOTTOM) {

    override fun draw(
        canvas: Canvas,
        text: CharSequence?,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        canvas.save()
        val transY =  top + (bottom - top) / 2 - drawable.bounds.height() / 2
        canvas.translate(x, transY.toFloat())
        drawable.setTint(paint.color)
        drawable.draw(canvas)
        canvas.restore()
    }
}