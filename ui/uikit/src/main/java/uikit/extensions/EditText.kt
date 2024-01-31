package uikit.extensions

import android.widget.EditText
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout

fun EditText.cursorToEnd() {
    try {
        setSelection(text.length)
    } catch (ignored: Throwable) { }
}

fun EditText.requestFocusWithSelection() {
    requestFocus()
    cursorToEnd()
}

fun EditText.focusWithKeyboard() {
    doOnLayout {
        post {
            requestFocusWithSelection()
            getInsetsControllerCompat()?.show(WindowInsetsCompat.Type.ime())
        }
    }
}

fun EditText.hideKeyboard() {
    clearFocus()
    getInsetsControllerCompat()?.hide(WindowInsetsCompat.Type.ime())
}
