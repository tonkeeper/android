package com.tonkeeper.fragment.jetton.list.holder

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonkeeper.fragment.jetton.list.JettonItem
import uikit.list.BaseListHolder

abstract class JettonHolder<I: JettonItem>(
    parent: ViewGroup,
    @LayoutRes resId: Int
): BaseListHolder<I>(parent, resId)