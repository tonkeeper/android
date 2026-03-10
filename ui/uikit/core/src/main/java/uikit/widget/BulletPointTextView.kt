package uikit.widget

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.withSave
import uikit.R
import uikit.extensions.drawable
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setPaddingStart

class BulletPointTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = android.R.attr.textViewStyle,
) : AppCompatTextView(context, attrs, defStyle) {

    private val bulletSize = context.getDimensionPixelSize(R.dimen.bulletSize)
    private val bulletOffset = context.getDimensionPixelSize(R.dimen.bulletOffset)
    private val bulletDrawable = context.drawable(R.drawable.bullet_point)
    private val textLineHeight: Float
        get() = paint.fontMetrics.let { it.bottom - it.top }

    init {
        bulletDrawable.setBounds(0, 0, bulletSize, bulletSize)
        setPaddingStart(bulletSize + bulletOffset)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.withSave {
            val y = (textLineHeight - bulletSize) / 2f
            translate(0f, y)
            bulletDrawable.draw(canvas)
        }
    }
}