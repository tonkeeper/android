package uikit.extensions

import android.text.Editable

fun Editable.deleteLast() {
    if (this.isNotEmpty()) {
        this.delete(this.length - 1, this.length)
    }
}

fun Editable.replaceLast(charSequence: CharSequence) {
    if (this.isNotEmpty()) {
        this.replace(this.length - 1, this.length, charSequence)
    }
}

fun Editable.replaceAll(charSequence: CharSequence) {
    this.replace(0, this.length, charSequence)
}