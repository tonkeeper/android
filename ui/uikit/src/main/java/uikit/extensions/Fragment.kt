package uikit.extensions

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity

fun Fragment.withAnimation(duration: Long = 120, block: () -> Unit) {
    view?.withAnimation(duration, block)
}
