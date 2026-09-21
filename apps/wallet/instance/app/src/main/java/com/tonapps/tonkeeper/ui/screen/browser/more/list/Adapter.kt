package com.tonapps.tonkeeper.ui.screen.browser.more.list

import android.view.ViewGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.ui.screen.browser.list.holder.ChainEmptyHolder
import com.tonapps.tonkeeper.ui.screen.browser.list.holder.ChainFilterHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem
import kotlinx.coroutines.flow.StateFlow

class Adapter(
    private val selectedChain: StateFlow<Network.Type?>,
    private val onChainSelected: (Network.Type?) -> Unit,
    private val environment: Environment,
): BaseListAdapter() {
    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when (viewType) {
            Item.TYPE_CHAIN_FILTER -> ChainFilterHolder<Item.ChainFilter>(
                parent = parent,
                selectedChain = selectedChain,
                onChainSelected = onChainSelected,
                environment = environment,
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
                listHorizontalPadding = 16.dp,
            )
            Item.TYPE_CHAIN_EMPTY -> ChainEmptyHolder<Item.ChainEmpty>(parent, environment)
            else -> Holder(parent)
        }
    }
}
