package uikit.widget.password

import android.text.InputFilter
import android.text.Spanned

class PasswordInputFilter: InputFilter {

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned?,
        dstart: Int,
        dend: Int
    ): CharSequence {
        return source.filterNot {
            it.isWhitespace()
        }
    }
}