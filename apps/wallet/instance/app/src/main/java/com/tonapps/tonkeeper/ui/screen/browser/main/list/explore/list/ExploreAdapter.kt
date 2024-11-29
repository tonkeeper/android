package com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.holder.ExploreAppExploreHolder
import com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.holder.ExploreBannersExploreHolder
import com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.holder.ExploreTitleExploreHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class ExploreAdapter(
    private val onMoreClick: (String) -> Unit
): BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when(viewType) {
            ExploreItem.TYPE_TITLE -> ExploreTitleExploreHolder(parent, onMoreClick)
            ExploreItem.TYPE_APP -> ExploreAppExploreHolder(parent)
            ExploreItem.TYPE_BANNERS -> ExploreBannersExploreHolder(parent)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.isNestedScrollingEnabled = true
    }

}