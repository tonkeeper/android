package uikit.navigation

import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.fragment.app.Fragment
import uikit.base.fragment.BaseFragment

interface Navigation {

    companion object {

        fun from(context: Context): Navigation? {
            if (context is Navigation) {
                return context
            }
            if (context is ContextWrapper) {
                return from(context.baseContext)
            }
            return null
        }

        fun Context.nav(): Navigation? = from(this)

        fun Fragment.nav(): Navigation? {
            val context = context ?: return null
            return from(context)
        }

        fun Dialog.nav() = from(context)
    }

    fun init(skipPasscode: Boolean)

    fun replace(fragment: BaseFragment, addToBackStack: Boolean)

    fun setFragmentResult(requestKey: String, result: Bundle = Bundle())

    fun setFragmentResultListener(
        requestKey: String,
        listener: ((requestKey: String, bundle: Bundle) -> Unit)
    )

    fun add(fragment: BaseFragment)

    fun remove(fragment: BaseFragment)

    fun openURL(url: String, external: Boolean = false)
}
