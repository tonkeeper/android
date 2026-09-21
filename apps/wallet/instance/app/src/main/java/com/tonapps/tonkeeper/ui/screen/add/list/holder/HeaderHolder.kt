package com.tonapps.tonkeeper.ui.screen.add.list.holder

import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import com.tonapps.tonkeeper.ui.screen.add.list.Item
import uikit.extensions.dp
import uikit.widget.TextHeaderView

class HeaderHolder(parent: ViewGroup): Holder<Item.Header>(TextHeaderView(parent.context)) {

    private val itemActionView = itemView as TextHeaderView

    init {
        itemView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            bottomMargin = 16.dp
        }
    }

    override fun onBind(item: Item.Header) {
        itemActionView.title = getString(item.titleResId)
        itemActionView.desciption = getString(item.subtitleResId)
    }

}