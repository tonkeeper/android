package com.tonkeeper.fragment.chart.list

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import uikit.list.BaseListHolder

abstract class ChartHolder<I: ChartItem>(
    parent: ViewGroup,
    @LayoutRes resId: Int
): BaseListHolder<I>(parent, resId)