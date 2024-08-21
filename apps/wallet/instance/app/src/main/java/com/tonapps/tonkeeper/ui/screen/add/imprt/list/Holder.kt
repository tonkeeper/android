package com.tonapps.tonkeeper.ui.screen.add.imprt.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.uikit.list.BaseListHolder
import uikit.widget.ActionCellView

class Holder(
    parent: ViewGroup,
    private val onClick: (Item) -> Unit,
): BaseListHolder<Item>(ActionCellView(parent.context)) {

    private val itemActionView = itemView as ActionCellView

    init {
        itemActionView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onBind(item: Item) {
        itemActionView.setOnClickListener { onClick(item) }
        itemActionView.iconRes = item.iconResId
        itemActionView.title = getString(item.titleResId)
        itemActionView.subtitle = getString(item.subtitleResId)
    }

}