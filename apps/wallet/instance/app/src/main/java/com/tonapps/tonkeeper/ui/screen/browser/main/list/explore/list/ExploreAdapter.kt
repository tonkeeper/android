package com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.ui.screen.browser.list.holder.ChainEmptyHolder
import com.tonapps.tonkeeper.ui.screen.browser.list.holder.ChainFilterHolder
import com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.holder.ExploreAdsHolder
import com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.holder.ExploreAppExploreHolder
import com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.holder.ExploreBannersExploreHolder
import com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.holder.ExploreTitleExploreHolder
import com.tonapps.tonkeeper.ui.screen.browser.main.list.explore.list.holder.ExploreSpaceHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem
import kotlinx.coroutines.flow.StateFlow

class ExploreAdapter(
    private val onMoreClick: (String) -> Unit,
    private val selectedChain: StateFlow<Network.Type?>,
    private val onChainSelected: (Network.Type?) -> Unit,
    private val environment: Environment,
): BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when(viewType) {
            ExploreItem.TYPE_TITLE -> ExploreTitleExploreHolder(parent, onMoreClick)
            ExploreItem.TYPE_APP -> ExploreAppExploreHolder(parent)
            ExploreItem.TYPE_BANNERS -> ExploreBannersExploreHolder(parent)
            ExploreItem.TYPE_ADS -> ExploreAdsHolder(parent)
            ExploreItem.TYPE_CHAIN_FILTER -> ChainFilterHolder<ExploreItem.ChainFilter>(
                parent = parent,
                selectedChain = selectedChain,
                onChainSelected = onChainSelected,
                environment = environment,
            )
            ExploreItem.TYPE_SPACE -> ExploreSpaceHolder(parent)
            ExploreItem.TYPE_CHAIN_EMPTY -> ChainEmptyHolder<ExploreItem.ChainEmpty>(parent, environment)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.isNestedScrollingEnabled = true
    }

}
