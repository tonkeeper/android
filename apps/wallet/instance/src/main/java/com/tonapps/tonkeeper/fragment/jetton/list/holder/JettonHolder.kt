package com.tonapps.tonkeeper.fragment.jetton.list.holder

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonapps.tonkeeper.fragment.jetton.list.JettonItem

abstract class JettonHolder<I: JettonItem>(
    parent: ViewGroup,
    @LayoutRes resId: Int
): com.tonapps.uikit.list.BaseListHolder<I>(parent, resId)