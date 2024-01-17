package uikit.navigation

import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import uikit.base.BaseFragment
import uikit.popup.ActionSheet

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

        val Context.navigation: Navigation?
            get() = from(this)

        val Fragment.navigation: Navigation?
            get() {
                val context = context ?: return null
                return from(context)
            }

        val Dialog.navigation: Navigation?
            get() = from(context)

        val ActionSheet.navigation: Navigation?
            get() = from(context)
    }

    fun setFragmentResult(requestKey: String, result: Bundle = Bundle())

    fun setFragmentResultListener(
        requestKey: String,
        listener: ((requestKey: String, bundle: Bundle) -> Unit)
    )

    fun initRoot(skipPasscode: Boolean, intent: Intent? = null)

    fun add(fragment: BaseFragment)

    fun remove(fragment: Fragment)

    fun openURL(url: String, external: Boolean = false)

    fun toast(message: String)
}
