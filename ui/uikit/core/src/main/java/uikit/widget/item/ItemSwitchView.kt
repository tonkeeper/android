package uikit.widget.item

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.uikit.list.ListCell
import uikit.R
import uikit.extensions.dp
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.setPaddingVertical
import uikit.extensions.useAttributes
import uikit.widget.SwitchView

class ItemSwitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : BaseItemView(context, attrs, defStyle) {

    private val textView: AppCompatTextView
    private val subtitleView: AppCompatTextView
    private val switchView: SwitchView

    var doOnCheckedChanged: ((checked: Boolean, byUser: Boolean) -> Unit)?
        get() = switchView.doCheckedChanged
        set(value) {
            switchView.doCheckedChanged = value
        }

    var text: String?
        get() = textView.text.toString()
        set(value) {
            textView.text = value
        }

    var subtitle: String?
        get() = subtitleView.text.toString()
        set(value) {
            subtitleView.text = value
            subtitleView.visibility = if (value.isNullOrEmpty()) {
                GONE
            } else {
                VISIBLE
            }
        }

    init {
        inflate(context, R.layout.view_item_switch, this)

        textView = findViewById(R.id.text)
        subtitleView = findViewById(R.id.subtitle)
        switchView = findViewById(R.id.check)

        setOnClickListener {
            setChecked(!isChecked(), true)
        }

        context.useAttributes(attrs, R.styleable.ItemSwitchView) {
            text = it.getString(R.styleable.ItemSwitchView_android_text)
            subtitle = it.getString(R.styleable.ItemSwitchView_android_subtitle)
            position = ListCell.from(it.getString(R.styleable.ItemSwitchView_position))
            setChecked(it.getBoolean(R.styleable.ItemSwitchView_android_checked, false), false)
        }
    }

    fun isChecked() = switchView.isChecked()

    fun setChecked(newChecked: Boolean, byUser: Boolean) {
        switchView.setChecked(newChecked, byUser)
    }

}