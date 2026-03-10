package uikit.widget.item

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.view.setPadding
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY))
    }
}