package uikit.widget.input

import android.content.Context
import android.text.InputFilter
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import androidx.appcompat.R
import androidx.appcompat.widget.AppCompatEditText
import com.tonapps.uikit.color.accentBlueColor
import uikit.extensions.setCursorColor

class InputTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyle) {

    private var formattingTextWatcher: TextWatcher? = null
    private var formattingInputFilter: InputFilter? = null

    init {
        setCursorColor(context.accentBlueColor)
    }

    fun setOnDoneActionListener(listener: () -> Unit) {
        onEditorAction(EditorInfo.IME_ACTION_DONE)
        setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                listener()
                true
            } else {
                false
            }
        }
    }

    fun setFormattingInputFilter(filter: InputFilter) {
        removePreviousFormattingInputFilter()
        formattingInputFilter = filter
        filters = filters.plus(filter)
    }

    fun setMaxLength(maxLength: Int) {
        val filter = InputFilter.LengthFilter(maxLength)
        filters = filters.filter {
            it !is InputFilter.LengthFilter
        }.plus(filter).toTypedArray()
    }

    private fun removePreviousFormattingInputFilter() {
        formattingInputFilter?.let { filters = filters.filter { it != formattingInputFilter }.toTypedArray() }
    }

    fun setFormattingTextWatcher(watcher: TextWatcher) {
        removePreviousFormattingTextWatcher()
        formattingTextWatcher = watcher
        addTextChangedListener(watcher)
    }

    private fun removePreviousFormattingTextWatcher() {
        formattingTextWatcher?.let { removeTextChangedListener(it) }
    }
}