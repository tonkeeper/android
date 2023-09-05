package com.tonkeeper.ui.fragment

import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment

open class BaseFragment(
    @LayoutRes layoutId: Int
): Fragment(layoutId)