package com.tonapps.tonkeeper.ui.screen.browser.explore.banners

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class BannersAdapter: BaseListAdapter() {

    private val listSize: Int
        get() = currentList.size

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return BannerHolder(parent)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.isNestedScrollingEnabled = true
    }

    override fun getItem(position: Int): BaseListItem {
        return super.getItem(position % listSize)
    }

    override fun getItemCount(): Int {
        if (listSize > 0) {
            return Int.MAX_VALUE
        }
        return 0
    }
}