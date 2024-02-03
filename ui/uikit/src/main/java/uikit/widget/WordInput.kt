package uikit.widget

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.view.doOnLayout
import uikit.R
import uikit.drawable.InputDrawable
import uikit.extensions.dp
import uikit.extensions.focusWithKeyboard
import uikit.extensions.hideKeyboard
import uikit.extensions.requestFocusWithSelection

class WordInput @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : RowLayout(context, attrs, defStyle),
    View.OnFocusChangeListener,
    TextView.OnEditorActionListener, TextWatcher {

    private val inputDrawable = InputDrawable(context)
    private val indexTextView: AppCompatTextView
    private val inputEditText: AppCompatEditText

    var doOnNext: (() -> Unit)? = null
    var doOnPrev: (() -> Unit)? = null
    var doOnFocus: ((focus: Boolean) -> Unit)? = null
    var doOnTextChanged: ((String) -> Unit)? = null

    var text: String
        get() = inputEditText.text.toString().trim()
        set(value) {
            inputEditText.setText(value)
        }

    private var ignorePressDelete = false

    init {
        background = inputDrawable

        inflate(context, R.layout.view_word_input, this)
        indexTextView = findViewById(R.id.index)
        inputEditText = findViewById(R.id.input)
        inputEditText.onFocusChangeListener = this
        inputEditText.setOnEditorActionListener(this)
        inputEditText.addTextChangedListener(this)
        inputEditText.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                return@setOnKeyListener onPressDelete()
            }
            return@setOnKeyListener false
        }

        setOnClickListener { focus() }
    }

    private fun onPressDelete(): Boolean {
        if (text.isEmpty()) {
            if (ignorePressDelete) {
                ignorePressDelete = false
                return false
            }
            doOnPrev?.invoke()
            return true
        }
        return false
    }

    fun focus(keyboard: Boolean = true) {
        if (keyboard) {
            inputEditText.focusWithKeyboard()
        } else {
            inputEditText.requestFocusWithSelection()
        }
    }

    fun hideKeyboard() {
        inputEditText.hideKeyboard()
    }

    fun setIndex(index: Int) {
        indexTextView.text = "$index:"
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(56.dp, MeasureSpec.EXACTLY))
    }

    override fun onFocusChange(v: View?, hasFocus: Boolean) {
        doOnFocus?.invoke(hasFocus)
        inputDrawable.active = hasFocus
    }

    override fun onEditorAction(v: TextView?, actionId: Int, event: KeyEvent?): Boolean {
        if (actionId == EditorInfo.IME_ACTION_NEXT) {
            doOnNext?.invoke()
            return true
        }
        return false
    }

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        if (count == 0) {
            ignorePressDelete = true
        }
    }

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

    }

    override fun afterTextChanged(s: Editable?) {
        doOnTextChanged?.invoke(s.toString())

        if (inputDrawable.error && s.isNullOrBlank()) {
            inputDrawable.error = false
        }
    }

    override fun isFocused(): Boolean {
        return inputEditText.isFocused
    }

    fun setError(error: Boolean) {
        inputDrawable.error = error
    }


}