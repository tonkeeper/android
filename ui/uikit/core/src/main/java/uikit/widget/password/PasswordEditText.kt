package uikit.widget.password

import android.content.Context
import android.os.Build
import android.text.Editable
import android.text.TextUtils
import android.util.AttributeSet
import androidx.annotation.RequiresApi
import androidx.appcompat.R
import androidx.appcompat.widget.AppCompatEditText

class PasswordEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyle) {

    init {
        imeOptions = imeOptions or 0x1000000
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun getAutofillType(): Int {
        return AUTOFILL_TYPE_NONE
    }

    fun clear() {
        text = Editable.Factory.getInstance().newEditable("")
    }

    fun getPassword(): CharArray {
        val size = length()
        val value = CharArray(size)
        val text = text
        if (size > 0 && text != null) {
            // SDK 37 adds CharSequence.getChars, which hides Editable.getChars from Kotlin.
            TextUtils.getChars(text, 0, size, value, 0)
        }
        return value
    }
}