package uikit.widget.item

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import uikit.R
import uikit.extensions.useAttributes
import uikit.list.ListCell

class ItemTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : BaseItemView(context, attrs, defStyle) {

    private val textView: AppCompatTextView
    val dataView: AppCompatTextView

    var text: String?
        get() = textView.text.toString()
        set(value) {
            textView.text = value
        }

    var data: String?
        get() = dataView.text.toString()
        set(value) {
            dataView.text = value
        }

    init {
        inflate(context, R.layout.view_item_text, this)

        textView = findViewById(R.id.text)
        dataView = findViewById(R.id.data)

        context.useAttributes(attrs, R.styleable.ItemTextView) {
            text = it.getString(R.styleable.ItemTextView_android_text)
            data = it.getString(R.styleable.ItemTextView_android_data)
            position = ListCell.from(it.getString(R.styleable.ItemTextView_position))
        }
    }
}