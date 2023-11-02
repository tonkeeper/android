package uikit.widget.item

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import uikit.R
import uikit.extensions.useAttributes
import uikit.list.ListCell

class ItemIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : BaseItemView(context, attrs, defStyle) {

    private val textView: AppCompatTextView
    private val iconView: AppCompatImageView

    var text: String?
        get() = textView.text.toString()
        set(value) {
            textView.text = value
        }

    var iconRes: Int = 0
        set(value) {
            iconView.setImageResource(value)
        }

    init {
        inflate(context, R.layout.view_item_icon, this)

        textView = findViewById(R.id.text)
        iconView = findViewById(R.id.icon)

        context.useAttributes(attrs, R.styleable.ItemIconView) {
            text = it.getString(R.styleable.ItemIconView_android_text)
            iconRes = it.getResourceId(R.styleable.ItemIconView_android_icon, R.drawable.ic_chevron_right_16)
            position = ListCell.from(it.getString(R.styleable.ItemIconView_position))
        }
    }
}