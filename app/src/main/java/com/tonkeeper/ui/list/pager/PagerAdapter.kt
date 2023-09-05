package com.tonkeeper.ui.list.pager

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonkeeper.ui.list.base.BaseListHolder
import com.tonkeeper.ui.list.base.BaseListItem
import com.tonkeeper.ui.list.base.BaseListAdapter

class PagerAdapter(
    items: List<PagerItem>
): BaseListAdapter<PagerHolder>(items) {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return PagerHolder(parent)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.overScrollMode = RecyclerView.OVER_SCROLL_NEVER
    }

}