package com.tonapps.tonkeeper.ui.screen.collectibles.manage.list

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.collectibles.manage.list.holder.AllHolder
import com.tonapps.tonkeeper.ui.screen.collectibles.manage.list.holder.CollectionHolder
import com.tonapps.tonkeeper.ui.screen.collectibles.manage.list.holder.SpaceHolder
import com.tonapps.tonkeeper.ui.screen.collectibles.manage.list.holder.TitleHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class Adapter(
    private val onClick: (Item.Collection) -> Unit,
    private val showAllClick: () -> Unit,
): BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when(viewType) {
            Item.TYPE_TITLE -> TitleHolder(parent)
            Item.TYPE_SPACE -> SpaceHolder(parent)
            Item.TYPE_ALL -> AllHolder(parent, showAllClick)
            Item.TYPE_COLLECTION -> CollectionHolder(parent, onClick)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

}