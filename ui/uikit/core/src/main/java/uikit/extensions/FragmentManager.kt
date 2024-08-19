package uikit.extensions

import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

inline fun <reified F> FragmentManager.findFragment(): F? {
    val f = fragments.find { it is F }
    return f as? F
}

inline fun <reified F> FragmentManager.isFragmentExists(): Boolean {
    return findFragment<F>() != null
}


fun FragmentManager.findFragments(id: Int): List<Fragment> {
    return fragments.filter { fragment ->
        val parent = fragment.view?.parent as? View ?: return@filter false
        parent.id == id
    }
}