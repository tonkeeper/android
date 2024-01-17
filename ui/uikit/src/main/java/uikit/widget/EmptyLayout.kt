package uikit.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import uikit.R
import uikit.extensions.useAttributes

class EmptyLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle) {

    var doOnButtonClick: ((first: Boolean) -> Unit)? = null
        set(value) {
            field = value
            firstButton.setOnClickListener { value?.invoke(true) }
            secondButton.setOnClickListener { value?.invoke(false) }
        }

    private val titleView: AppCompatTextView
    private val subtitleView: AppCompatTextView
    private val firstButton: Button
    private val secondButton: Button

    init {
        orientation = VERTICAL
        inflate(context, R.layout.view_empty, this)

        titleView = findViewById(R.id.empty_placeholder_title)
        subtitleView = findViewById(R.id.empty_placeholder_subtitle)
        firstButton = findViewById(R.id.empty_placeholder_button_first)
        secondButton = findViewById(R.id.empty_placeholder_button_second)

        context.useAttributes(attrs, R.styleable.EmptyLayout) {
            titleView.text = it.getString(R.styleable.EmptyLayout_android_title)
            subtitleView.text = it.getString(R.styleable.EmptyLayout_android_description)
            firstButton.text = it.getString(R.styleable.EmptyLayout_android_positiveButtonText)
            secondButton.text = it.getString(R.styleable.EmptyLayout_android_negativeButtonText)
        }
    }

}