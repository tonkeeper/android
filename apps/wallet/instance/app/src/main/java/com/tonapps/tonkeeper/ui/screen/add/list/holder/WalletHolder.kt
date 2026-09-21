package com.tonapps.tonkeeper.ui.screen.add.list.holder

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.add.list.Item
import com.tonapps.wallet.localization.Localization
import uikit.extensions.withBlueBadge
import uikit.extensions.withCutIcons
import uikit.extensions.withDefaultBadge
import uikit.widget.ActionCellView

class WalletHolder(
    parent: ViewGroup,
    private val onClick: (Item.Wallet) -> Unit,
): Holder<Item.Wallet>(ActionCellView(parent.context)) {

    private val itemActionView = itemView as ActionCellView

    override fun onBind(item: Item.Wallet) {
        itemActionView.setOnClickListener { onClick(item) }
        itemActionView.iconRes = item.iconResId
        var title: CharSequence = getString(item.titleResId)
        item.tagResId?.let { tag ->
            title = title.withBlueBadge(context, tag)
        }
        if (item.chainIconResIds.isNotEmpty()) {
            title = title.withCutIcons(context, item.chainIconResIds, showMore = true)
        }
        itemActionView.title = title
        itemActionView.subtitle = getString(item.subtitleResId)
    }
}
