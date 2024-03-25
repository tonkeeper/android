package uikit.extensions

import android.widget.TextView

fun TextView.setTextOrGone(text: CharSequence?) {
    visibility = if (text.isNullOrEmpty()) {
        android.view.View.GONE
    } else {
        setText(text)
        android.view.View.VISIBLE
    }
}