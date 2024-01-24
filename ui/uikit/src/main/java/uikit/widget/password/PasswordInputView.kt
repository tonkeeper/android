package uikit.widget.password

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.postDelayed
import androidx.core.widget.doAfterTextChanged
import uikit.R
import uikit.HapticHelper
import uikit.drawable.InputDrawable
import uikit.extensions.focusWithKeyboard
import uikit.extensions.getDimensionPixelSize
import uikit.extensions.hideKeyboard
import uikit.extensions.startSnakeAnimation

class PasswordInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle), View.OnFocusChangeListener {

    private companion object {
        private const val errorAnimationDuration = 400L
    }

    private val inputView: AppCompatEditText
    private val inputDrawable = InputDrawable(context)

    var text: Editable?
        get() = inputView.text
        set(value) {
            inputView.text = value
        }

    var error: Boolean
        get() = inputDrawable.error
        set(value) {
            inputDrawable.error = value
            if (value) {
                HapticHelper.warning(context)
            }
        }

    val isEmpty: Boolean
        get() = text.isNullOrBlank()

    init {
        background = inputDrawable
        inflate(context, R.layout.view_input_password, this)
        inputView = findViewById(R.id.input)
        inputView.onFocusChangeListener = this
        inputView.transformationMethod = PasswordTransformationMethod.getInstance()
        inputView.filters = arrayOf(PasswordInputFilter())

        setOnClickListener {
            inputView.focusWithKeyboard()
        }
    }

    fun doAfterTextChanged(
        action: (text: Editable?) -> Unit
    ): TextWatcher = inputView.doAfterTextChanged(action)

    fun focusWithKeyboard() {
        inputView.focusWithKeyboard()
    }

    fun hideKeyboard() {
        inputView.hideKeyboard()
    }

    fun failedPassword() {
        error = true
        startSnakeAnimation(duration = errorAnimationDuration)
        postDelayed(errorAnimationDuration) {
            text = null
        }
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        inputView.isEnabled = enabled
    }

    override fun onFocusChange(v: View?, hasFocus: Boolean) {
        inputDrawable.active = hasFocus
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = context.getDimensionPixelSize(uikit.R.dimen.barHeight)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY))
    }
}