package com.tonkeeper.core.history.list.holder

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonkeeper.core.history.list.item.HistoryItem
import uikit.list.BaseListHolder

abstract class HistoryHolder<I: HistoryItem>(
    parent: ViewGroup,
    @LayoutRes resId: Int
): BaseListHolder<I>(parent, resId)