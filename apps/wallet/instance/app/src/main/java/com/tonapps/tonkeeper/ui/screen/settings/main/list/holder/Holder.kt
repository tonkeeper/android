package com.tonapps.tonkeeper.ui.screen.settings.main.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.settings.main.list.Item
import com.tonapps.uikit.list.BaseListHolder
import uikit.extensions.inflate

abstract class Holder<I: Item>(
    view: View,
    val onClick: ((Item) -> Unit)
): BaseListHolder<I>(view) {

    constructor(
        parent: ViewGroup,
        @LayoutRes resId: Int,
        onClick: ((Item) -> Unit)
    ) : this(
        parent.inflate(resId),
        onClick
    )

    init {
        itemView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

}