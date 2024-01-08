package uikit.extensions

import androidx.fragment.app.FragmentManager

inline fun <reified F> FragmentManager.findFragment(): F? {
    val f = fragments.find { it is F }
    return f as? F
}