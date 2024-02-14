package uikit.extensions

import androidx.fragment.app.Fragment

fun Fragment.withAnimation(duration: Long = 120, block: () -> Unit) {
    view?.withAnimation(duration, block)
}
