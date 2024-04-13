package com.tonapps.tonkeeper.ui.screen.settings.language.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.extensions.capitalized
import com.tonapps.uikit.icon.UIKitIcon
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.wallet.localization.Language
import com.tonapps.wallet.localization.Localization
import uikit.widget.item.ItemIconView

class Holder(
    parent: ViewGroup,
    private val onClick: (item: Item) -> Unit
): BaseListHolder<Item>(ItemIconView(parent.context)) {

    private val itemIconView = itemView as ItemIconView

    init {
        itemIconView.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onBind(item: Item) {
        itemIconView.setOnClickListener { onClick(item) }
        itemIconView.position = item.position
        if (item.code == Language.DEFAULT) {
            itemIconView.text = getString(Localization.system)
            itemIconView.description = ""
        } else {
            itemIconView.text = item.name.capitalized
            itemIconView.description = item.nameLocalized
        }

        if (item.selected) {
            itemIconView.iconRes = UIKitIcon.ic_donemark_thin_28
        } else {
            itemIconView.iconRes = 0
        }
    }
}