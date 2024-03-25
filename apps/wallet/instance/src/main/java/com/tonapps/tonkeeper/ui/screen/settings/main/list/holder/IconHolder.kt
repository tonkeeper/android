package com.tonapps.tonkeeper.ui.screen.settings.main.list.holder

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.settings.main.list.Item
import com.tonapps.uikit.color.accentBlueColor
import com.tonapps.uikit.color.iconSecondaryColor
import uikit.extensions.drawable
import uikit.widget.item.ItemIconView

class IconHolder(
    parent: ViewGroup,
    onClick: ((Item) -> Unit)
): Holder<Item.Icon>(ItemIconView(parent.context), onClick) {

    private val itemIconView = itemView as ItemIconView

    override fun onBind(item: Item.Icon) {
        itemIconView.background = item.position.drawable(context)
        itemIconView.setOnClickListener { onClick.invoke(item) }
        itemIconView.text = getString(item.titleRes)
        if (item.secondaryIcon) {
            itemIconView.setIconTintColor(context.iconSecondaryColor)
        } else {
            itemIconView.setIconTintColor(context.accentBlueColor)
        }
        itemIconView.iconRes = item.iconRes
        itemIconView.text = getString(item.titleRes)
    }
}