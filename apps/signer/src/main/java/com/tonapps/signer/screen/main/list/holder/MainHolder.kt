package com.tonapps.signer.screen.main.list.holder

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonapps.signer.screen.main.list.MainItem

abstract class MainHolder<I: MainItem>(
    parent: ViewGroup,
    @LayoutRes resId: Int
): com.tonapps.uikit.list.BaseListHolder<I>(parent, resId)
