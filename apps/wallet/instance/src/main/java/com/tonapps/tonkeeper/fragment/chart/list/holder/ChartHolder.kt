package com.tonapps.tonkeeper.fragment.chart.list.holder

import android.view.ViewGroup
import androidx.annotation.LayoutRes
import com.tonapps.tonkeeper.fragment.chart.list.ChartItem

abstract class ChartHolder<I: ChartItem>(
    parent: ViewGroup,
    @LayoutRes resId: Int
): com.tonapps.uikit.list.BaseListHolder<I>(parent, resId)