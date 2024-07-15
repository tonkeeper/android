package uikit.drawable

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.view.Gravity
import androidx.core.graphics.withSave
import uikit.base.BaseDrawable
import uikit.extensions.dp

open class TextDrawable: BaseDrawable() {

    var text: CharSequence = ""
        set(value) {
            if (field != value) {
                field = value
                staticLayout = null
            }
        }

    var paint: TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        set(value) {
            if (field != value) {
                field = value
                staticLayout = null
            }
        }

    var singleLine: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                staticLayout = null
            }
        }

    var gravity: Int = Gravity.NO_GRAVITY
        set(value) {
            if (field != value) {
                field = value
                staticLayout = null
            }
        }

    private var staticLayout: StaticLayout? = null

    var translateX: Float = 0f
    var translateY: Float = 0f

    val height: Int
        get() = requireStaticLayout().height

    fun requireStaticLayout(): Layout {
        if (staticLayout == null) {
            val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, bounds.width())
            builder.setIncludePad(true)
            builder.setAlignment(Layout.Alignment.ALIGN_NORMAL)
            if (singleLine) {
                builder.setMaxLines(1)
            }

            staticLayout = builder.build()
            translateX = bounds.left.toFloat()

            if (gravity and Gravity.CENTER_VERTICAL == Gravity.CENTER_VERTICAL) {
                translateY = (bounds.height() - staticLayout!!.height) / 2f
            }
            onStaticLayoutUpdated()
        }
        return staticLayout!!
    }

    open fun onStaticLayoutUpdated() {
        // override this method to get notified when static layout is updated
    }

    override fun draw(canvas: Canvas) {
        val staticLayout = requireStaticLayout()
        canvas.translate(translateX, translateY)
        staticLayout.draw(canvas)
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        staticLayout = null
    }

    override fun getIntrinsicWidth() = requireStaticLayout().width

    override fun getIntrinsicHeight() = requireStaticLayout().height
}