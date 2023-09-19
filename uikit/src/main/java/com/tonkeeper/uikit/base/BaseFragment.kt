package com.tonkeeper.uikit.base

import android.text.SpannableString
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.tonkeeper.uikit.extensions.getSpannable

open class BaseFragment(@LayoutRes layoutId: Int): Fragment(layoutId) {

    fun getSpannable(@StringRes id: Int): SpannableString {
        return requireContext().getSpannable(id)
    }
}