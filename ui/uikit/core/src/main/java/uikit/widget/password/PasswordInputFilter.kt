package uikit.widget.password

import android.text.InputFilter
import android.text.Spanned

class PasswordInputFilter: InputFilter {

    companion object {
        private const val DIGITS_STR = "0123456789"
        private const val UPPERS_STR = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        private const val LOWERS_STR = "abcdefghijklmnopqrstuvwxyz"
        private const val SYMBOLS_STR = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
        private const val ALLOWED_CHARACTERS = DIGITS_STR + UPPERS_STR + LOWERS_STR + SYMBOLS_STR
    }

    override fun filter(
        source: CharSequence,
        start: Int,
        end: Int,
        dest: Spanned?,
        dstart: Int,
        dend: Int
    ): CharSequence {
        return source.filter { ALLOWED_CHARACTERS.contains(it) }
    }
}