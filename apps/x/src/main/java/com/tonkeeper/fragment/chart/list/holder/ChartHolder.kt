package com.tonkeeper.fragment.chart.list.holder

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonkeeper.fragment.chart.list.ChartItem
import uikit.list.BaseListHolder

abstract class ChartHolder<I: ChartItem>(
    parent: ViewGroup,
    @LayoutRes resId: Int
): BaseListHolder<I>(parent, resId)