package com.tonapps.tonkeeper.ui.screen.settings.main.list

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.settings.main.list.holder.AccountHolder
import com.tonapps.tonkeeper.ui.screen.settings.main.list.holder.IconHolder
import com.tonapps.tonkeeper.ui.screen.settings.main.list.holder.LogoHolder
import com.tonapps.tonkeeper.ui.screen.settings.main.list.holder.SpaceHolder
import com.tonapps.tonkeeper.ui.screen.settings.main.list.holder.TextHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class Adapter(
    private val onClick: ((Item) -> Unit)
): BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when(viewType) {
            Item.TYPE_ACCOUNT -> AccountHolder(parent, onClick)
            Item.TYPE_SPACE -> SpaceHolder(parent, onClick)
            Item.TYPE_TEXT -> TextHolder(parent, onClick)
            Item.TYPE_ICON -> IconHolder(parent, onClick)
            Item.TYPE_LOGO -> LogoHolder(parent, onClick)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

}