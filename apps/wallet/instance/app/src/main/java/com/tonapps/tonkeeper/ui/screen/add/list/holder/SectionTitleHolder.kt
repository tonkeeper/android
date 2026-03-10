package com.tonapps.tonkeeper.ui.screen.add.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.core.view.marginTop
import com.tonapps.tonkeeper.ui.screen.add.list.Item
import uikit.widget.TextHeaderView

class SectionTitleHolder(parent: ViewGroup): Holder<Item.SectionTitle>(TextHeaderView(parent.context)) {

    private val itemActionView = itemView as TextHeaderView

    override fun onBind(item: Item.SectionTitle) {
        itemActionView.titleView.visibility = View.GONE
        (itemActionView.descriptionView.layoutParams as ViewGroup.MarginLayoutParams)
            .apply { topMargin = 0 }
            .also { itemActionView.descriptionView.layoutParams = it }
        itemActionView.desciption = getString(item.titleResId)
    }

}