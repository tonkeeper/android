package com.tonapps.tonkeeper.ui.screen.settings.main.list.holder

import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.view.ViewGroup
import com.tonapps.tonkeeper.extensions.getTitle
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
        if (item.secondaryIcon) {
            itemIconView.setIconTintColor(context.iconSecondaryColor)
        } else {
            itemIconView.setIconTintColor(context.accentBlueColor)
        }
        if (item is Item.Logout) {
            val builder = SpannableStringBuilder(getString(item.titleRes))
            builder.append(" ")
            builder.append(item.label.getTitle(context, itemIconView.textView))
            itemIconView.text = builder
        } else {
            itemIconView.text = getString(item.titleRes)
        }
        itemIconView.iconRes = item.iconRes
        itemIconView.dot = item.dot
    }
}