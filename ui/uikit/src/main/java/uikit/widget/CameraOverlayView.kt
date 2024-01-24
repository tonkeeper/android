package uikit.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import uikit.extensions.dp
import uikit.extensions.getDimension
import uikit.extensions.getDimensionPixelSize

class CameraOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private companion object {
        private val color = Color.parseColor("#CC000000")
    }

    private val strokeSize = context.getDimension(uikit.R.dimen.actionSize)
    private val horizontalMargin = context.getDimension(uikit.R.dimen.actionSize)
    private val cornerRadius = context.getDimension(uikit.R.dimen.cornerMedium)

    private val centerRect = RectF()
    private val path = Path()
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f.dp
        strokeJoin = Paint.Join.MITER
        strokeCap = Paint.Cap.ROUND
        pathEffect = CornerPathEffect(cornerRadius)
    }

    private val transparentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.TRANSPARENT
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(color)
        canvas.drawRoundRect(centerRect, cornerRadius, cornerRadius, transparentPaint)
        canvas.drawPath(path, pathPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val size = measuredWidth.coerceAtMost(measuredHeight) - (horizontalMargin * 2)
        val left = (measuredWidth - size) / 2f
        val top = (measuredHeight - size) / 2f
        centerRect.set(left, top, left + size, top + size)
        updatePath(left, top, left + size, top + size)
    }

    private fun updatePath(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) {
        path.reset()

        path.moveTo(left, top + strokeSize)
        path.lineTo(left, top)
        path.lineTo(left + strokeSize, top)

        path.moveTo(right - strokeSize, top)
        path.lineTo(right, top)
        path.lineTo(right, top + strokeSize)

        path.moveTo(left, bottom - strokeSize)
        path.lineTo(left, bottom)
        path.lineTo(left + strokeSize, bottom)

        path.moveTo(right - strokeSize, bottom)
        path.lineTo(right, bottom)
        path.lineTo(right, bottom - strokeSize)
    }

}