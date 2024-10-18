package com.tonapps.tonkeeper.ui.screen.send.contacts.main.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.send.contacts.main.list.holder.LatestHolder
import com.tonapps.tonkeeper.ui.screen.send.contacts.main.list.holder.LoaderHolder
import com.tonapps.tonkeeper.ui.screen.send.contacts.main.list.holder.MyWalletHolder
import com.tonapps.tonkeeper.ui.screen.send.contacts.main.list.holder.SavedHolder
import com.tonapps.tonkeeper.ui.screen.send.contacts.main.list.holder.SpaceHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class Adapter(
    private val onClick: (Item) -> Unit,
    private val onAction: (item: Item, actionId: Long) -> Unit
): BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when(viewType) {
            Item.TYPE_MY_WALLET -> MyWalletHolder(parent, onClick)
            Item.TYPE_SPACE -> SpaceHolder(parent)
            Item.TYPE_LATEST_CONTACT -> LatestHolder(parent, onClick, onAction)
            Item.TYPE_SAVED_CONTACT -> SavedHolder(parent, onClick, onAction)
            Item.TYPE_LOADING -> LoaderHolder(parent)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.isNestedScrollingEnabled = true
    }
}