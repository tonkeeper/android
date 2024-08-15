package com.tonapps.tonkeeper.ui.screen.send.contacts.list

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.send.contacts.list.holder.LatestHolder
import com.tonapps.tonkeeper.ui.screen.send.contacts.list.holder.MyWalletHolder
import com.tonapps.tonkeeper.ui.screen.send.contacts.list.holder.SpaceHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class Adapter(
    private val onClick: (Item) -> Unit
): BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when(viewType) {
            Item.TYPE_MY_WALLET -> MyWalletHolder(parent, onClick)
            Item.TYPE_SPACE -> SpaceHolder(parent)
            Item.TYPE_LATEST_CONTACT -> LatestHolder(parent, onClick)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
}