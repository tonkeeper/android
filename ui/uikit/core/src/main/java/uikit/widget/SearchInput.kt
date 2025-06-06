package uikit.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.widget.doOnTextChanged
import uikit.R
import uikit.extensions.dp
import uikit.extensions.focusWithKeyboard
import uikit.extensions.hideKeyboard
import uikit.extensions.useAttributes

class SearchInput @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : LinearLayoutCompat(context, attrs, defStyle) {

    var doOnTextChanged: ((text: CharSequence?) -> Unit)? = null

    var text: CharSequence?
        get() = fieldView.text
        set(value) {
            fieldView.text?.let {
                it.clear()
                it.append(value)
            }
        }

    private val fieldView: AppCompatEditText
    private val actionView: AppCompatTextView

    init {
        orientation = HORIZONTAL
        inflate(context, R.layout.view_search_input, this)

        fieldView = findViewById(R.id.field)
        fieldView.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
        fieldView.doOnTextChanged { text, _, _, _ ->
            doOnTextChanged?.invoke(text)
        }

        actionView = findViewById(R.id.action)
        actionView.setOnClickListener {
            cancel()
        }

        context.useAttributes(attrs, R.styleable.SearchInput) {
            fieldView.hint = it.getString(R.styleable.SearchInput_android_hint)
            setAction(it.getString(R.styleable.SearchInput_android_button))
        }
    }

    private fun setAction(action: String?) {
        if (action.isNullOrBlank()) {
            actionView.visibility = View.GONE
        } else {
            actionView.visibility = View.VISIBLE
            actionView.text = action
        }
    }

    fun cancel() {
        fieldView.text?.clear()
        fieldView.hideKeyboard()
    }

    fun focus() {
        fieldView.focusWithKeyboard()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = 80.dp
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }
}