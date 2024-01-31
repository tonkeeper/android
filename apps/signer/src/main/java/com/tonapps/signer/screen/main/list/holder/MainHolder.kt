package com.tonapps.signer.screen.main.list.holder

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonapps.signer.screen.main.list.MainItem
import uikit.list.BaseListHolder

abstract class MainHolder<I: MainItem>(
    parent: ViewGroup,
    @LayoutRes resId: Int
): BaseListHolder<I>(parent, resId)
