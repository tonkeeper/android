package com.tonapps.tonkeeper.ui.screen.settings.theme.list.holder

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.extensions.capitalized
import com.tonapps.tonkeeper.ui.screen.settings.theme.list.Item
import com.tonapps.uikit.icon.UIKitIcon
import uikit.widget.item.ItemIconView

class ThemeHolder(
    parent: ViewGroup,
    private val onClickTheme: (item: Item.Theme) -> Unit
): Holder<Item.Theme>(ItemIconView(parent.context)) {

    private val itemIconView = itemView as ItemIconView

    init {
        itemIconView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onBind(item: Item.Theme) {
        itemIconView.setOnClickListener { onClickTheme(item) }
        itemIconView.position = item.position
        itemIconView.text = item.theme.key.capitalized
        itemIconView.iconRes = if (item.selected) {
            UIKitIcon.ic_done_16
        } else {
            0
        }
    }

}