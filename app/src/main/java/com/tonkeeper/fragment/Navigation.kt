package com.tonkeeper.fragment

import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.Fragment
import com.tonkeeper.uikit.base.BaseFragment

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

        fun Fragment.nav(): Navigation? {
            val context = context ?: return null
            return from(context)
        }

        fun Dialog.nav() = from(context)
    }

    fun replace(fragment: BaseFragment)
}
