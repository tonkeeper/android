package com.tonapps.tonkeeper.ui.screen.add.list.holder

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.add.list.Item
import uikit.widget.ActionCellView

class WalletHolder(
    parent: ViewGroup,
    private val onClick: (Item.Wallet) -> Unit,
): Holder<Item.Wallet>(ActionCellView(parent.context)) {

    private val itemActionView = itemView as ActionCellView

    override fun onBind(item: Item.Wallet) {
        itemActionView.setOnClickListener { onClick(item) }
        itemActionView.iconRes = item.iconResId
        itemActionView.title = getString(item.titleResId)
        itemActionView.subtitle = getString(item.subtitleResId)
    }

}