package com.tonapps.tonkeeper.ui.screen.add.list.holder

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.add.list.Item
import uikit.widget.TextHeaderView

class HeaderHolder(parent: ViewGroup): Holder<Item.Header>(TextHeaderView(parent.context)) {

    private val itemActionView = itemView as TextHeaderView

    override fun onBind(item: Item.Header) {
        itemActionView.title = getString(item.titleResId)
        itemActionView.desciption = getString(item.subtitleResId)
    }

}