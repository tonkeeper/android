package com.tonkeeper.uikit.base.fragment

import android.os.Bundle
import android.text.SpannableString
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.tonkeeper.uikit.R
import com.tonkeeper.uikit.extensions.getSpannable
import com.tonkeeper.uikit.widget.BackHeaderView
import com.tonkeeper.uikit.widget.LoaderView

open class BaseFragment(
    @LayoutRes layoutId: Int
): Fragment(layoutId) {

    val window: Window?
        get() = activity?.window

    open val secure: Boolean = false

    fun getSpannable(@StringRes id: Int): SpannableString {
        return requireContext().getSpannable(id)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setBackgroundResource(R.color.backgroundPage)
    }

    override fun onResume() {
        super.onResume()
        if (secure) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onPause() {
        super.onPause()
        if (secure) {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}