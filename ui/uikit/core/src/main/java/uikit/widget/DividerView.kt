package uikit.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.tonapps.uikit.color.separatorCommonColor
import uikit.extensions.dp

class DividerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.separatorCommonColor
        strokeWidth = .5f.dp
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bottom = measuredHeight.toFloat()
        canvas.drawLine(0f, bottom, measuredWidth.toFloat(), bottom, paint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(1.dp, MeasureSpec.EXACTLY))
    }
}