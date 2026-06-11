package uikit.widget.item

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ImageSpan
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatTextView
import uikit.R
import uikit.extensions.dp
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setPaddingHorizontal
import uikit.extensions.useAttributes
import uikit.widget.RowLayout

class ItemLineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RowLayout(context, attrs, defStyle) {

    private val size = context.getDimensionPixelSize(R.dimen.tertiaryHeight)

    private val nameView: AppCompatTextView
    private val valueView: AppCompatTextView

    var name: CharSequence?
        get() = nameView.text
        set(value) {
            nameView.text = value
        }

    var value: CharSequence?
        get() = valueView.text
        set(value) {
            valueView.text = value
        }

    init {
        inflate(context, R.layout.view_item_line, this)
        setPaddingHorizontal(16.dp)
        nameView = findViewById(R.id.line_name)
        valueView = findViewById(R.id.line_value)

        context.useAttributes(attrs, R.styleable.ItemLineView) {
            name = it.getString(R.styleable.ItemLineView_android_name)
            value = it.getString(R.styleable.ItemLineView_android_value)
        }
    }

    fun setValueColor(@ColorInt color: Int) {
        valueView.setTextColor(color)
    }

    fun setValueIcon(drawable: Drawable?) {
        if (drawable == null) {
            valueView.setCompoundDrawables(null, null, null, null)
            return
        }
        val size = 16.dp
        drawable.setBounds(0, 0, size, size)
        valueView.compoundDrawablePadding = 4.dp
        valueView.setCompoundDrawables(null, null, drawable, null)
    }

    fun setInfoIcon(drawable: Drawable?) {
        val baseText = name?.toString().orEmpty()
        if (drawable == null) {
            nameView.text = baseText
            return
        }
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        val builder = SpannableStringBuilder(baseText.trim())
        builder.append("  ")
        val start = builder.length - 1
        builder.setSpan(CenteredIconSpan(drawable), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        nameView.text = builder
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY))
    }

    private class CenteredIconSpan(drawable: Drawable) : ImageSpan(drawable, ALIGN_BOTTOM) {

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
            val fm = paint.fontMetricsInt
            val transY = y + (fm.ascent + fm.descent) / 2 - drawable.bounds.height() / 2
            canvas.save()
            canvas.translate(x, transY.toFloat())
            drawable.draw(canvas)
            canvas.restore()
        }
    }
}