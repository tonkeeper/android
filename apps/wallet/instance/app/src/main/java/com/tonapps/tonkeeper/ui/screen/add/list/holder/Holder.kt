package com.tonapps.tonkeeper.ui.screen.add.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.add.list.Item
import com.tonapps.uikit.list.BaseListHolder

abstract class Holder<I: Item>(
    view: View,
): BaseListHolder<I>(view) {

    init {
        itemView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
}