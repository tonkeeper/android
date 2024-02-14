package uikit.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.tonapps.uikit.color.UIKitColor
import uikit.R
import uikit.extensions.dp
import uikit.extensions.getDimensionPixelSize

class ColorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val activeStrokeSize = 5f.dp
    private val activeStrokeSizeHalf = activeStrokeSize / 2
    private val activeOffset = 3f.dp + activeStrokeSizeHalf
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(UIKitColor.backgroundPage)
        style = Paint.Style.STROKE
        strokeWidth = activeStrokeSize
    }

    var color: Int
        set(value) {
            paint.color = value
            invalidate()
        }
        get() = paint.color

    var active: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawOval(0f, 0f, measuredWidth.toFloat(), measuredHeight.toFloat(), paint)
        if (active) {
            canvas.drawOval(
                activeOffset,
                activeOffset,
                measuredWidth.toFloat() - activeOffset,
                measuredHeight.toFloat() - activeOffset,
                activePaint
            )
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = minOf(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
        setMeasuredDimension(size, size)
    }

    override fun hasOverlappingRendering() = false
}