package com.tonapps.tonkeeper.core.history.list.holder

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonapps.tonkeeper.core.history.list.item.HistoryItem

abstract class HistoryHolder<I: HistoryItem>(
    parent: ViewGroup,
    @LayoutRes resId: Int
): com.tonapps.uikit.list.BaseListHolder<I>(parent, resId)