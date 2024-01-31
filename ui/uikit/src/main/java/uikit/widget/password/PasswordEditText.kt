package uikit.widget.password

import android.content.Context
import android.os.Build
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

    fun getPassword(): CharArray {
        val size = length()
        val value = CharArray(size)
        if (size > 0 && text != null) {
            text?.getChars(0, size, value, 0)
        }
        return value
    }
}