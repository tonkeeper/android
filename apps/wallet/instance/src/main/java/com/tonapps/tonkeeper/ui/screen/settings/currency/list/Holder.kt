package com.tonapps.tonkeeper.ui.screen.settings.currency.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.uikit.list.BaseListHolder
import uikit.widget.item.ItemIconView

class Holder(
    parent: ViewGroup,
    private val onClick: (currency: String) -> Unit
): BaseListHolder<Item>(ItemIconView(parent.context)) {

    private val itemIconView = itemView as ItemIconView

    init {
        itemIconView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onBind(item: Item) {
        itemIconView.setOnClickListener { onClick(item.currency) }
        itemIconView.position = item.position
        itemIconView.text = item.currency
        itemIconView.description = context.getString(item.nameResId)

        if (item.selected) {
            itemIconView.iconRes = UIKitIcon.ic_donemark_thin_28
        } else {
            itemIconView.iconRes = 0
        }
    }

}