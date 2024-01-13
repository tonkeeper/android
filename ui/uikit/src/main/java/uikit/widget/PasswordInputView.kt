package uikit.widget

import android.content.Context
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import androidx.appcompat.R
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.setPadding
import uikit.drawable.InputDrawable
import uikit.extensions.getColor
import uikit.extensions.getDimensionPixelSize

class PasswordInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyle), View.OnFocusChangeListener {

    private val inputDrawable = InputDrawable(context)

    var error: Boolean
        get() = inputDrawable.error
        set(value) {
            inputDrawable.error = value
        }

    init {
        background = inputDrawable
        super.setOnFocusChangeListener(this)
        setTextColor(getColor(uikit.R.color.textSecondary))
        setPadding(0)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        typeface = ResourcesCompat.getFont(context, uikit.R.font.montserrat_bold)
        gravity = Gravity.CENTER
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    override fun onFocusChange(v: View?, hasFocus: Boolean) {
        inputDrawable.active = hasFocus
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = context.getDimensionPixelSize(uikit.R.dimen.barHeight)
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY))
    }
}