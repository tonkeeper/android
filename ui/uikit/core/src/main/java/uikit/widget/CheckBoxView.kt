package uikit.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import com.tonapps.uikit.color.backgroundContentTintColor
import com.tonapps.uikit.color.buttonPrimaryBackgroundColor
import com.tonapps.uikit.icon.UIKitIcon
import uikit.extensions.dp
import uikit.extensions.getDrawable

class CheckBoxView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private companion object {
        private val size = 22.dp
        private val radius = 6f.dp
        private val strokeSize = 2f.dp
        private val halfStrokeSize = strokeSize / 2f
    }

    var doOnCheckedChanged: ((Boolean) -> Unit)? = null

    private val checkDrawable: Drawable by lazy {
        val drawable = getDrawable(UIKitIcon.ic_done_bold_16)
        val width = drawable.intrinsicWidth
        val height = drawable.intrinsicHeight
        drawable.setBounds(
            (size - width) / 2,
            (size - height) / 2,
            (size + width) / 2,
            (size + height) / 2
        )
        drawable
    }

    private val defaultPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.backgroundContentTintColor
        style = Paint.Style.STROKE
        strokeWidth = strokeSize
    }

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.buttonPrimaryBackgroundColor
    }

    var checked: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                doOnCheckedChanged?.invoke(value)
                invalidate()
            }
        }

    init {
        setOnClickListener {
            toggle()
        }
    }

    fun toggle() {
        checked = !checked
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (checked) {
            canvas.drawRoundRect(
                0f,
                0f,
                size.toFloat(),
                size.toFloat(),
                radius,
                radius,
                activePaint
            )
            checkDrawable.draw(canvas)
        } else {
            canvas.drawRoundRect(
                halfStrokeSize,
                halfStrokeSize,
                size - halfStrokeSize,
                size - halfStrokeSize,
                radius,
                radius,
                defaultPaint
            )
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
        super.onMeasure(size, size)
    }

    override fun hasOverlappingRendering() = false
}