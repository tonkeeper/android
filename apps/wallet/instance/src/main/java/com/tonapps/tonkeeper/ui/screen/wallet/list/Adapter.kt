package com.tonapps.tonkeeper.ui.screen.wallet.list

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.wallet.list.holder.ActionsHolder
import com.tonapps.tonkeeper.ui.screen.wallet.list.holder.BalanceHolder
import com.tonapps.tonkeeper.ui.screen.wallet.list.holder.SkeletonHolder
import com.tonapps.tonkeeper.ui.screen.wallet.list.holder.SpaceHolder
import com.tonapps.tonkeeper.ui.screen.wallet.list.holder.TokenHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class Adapter(
    private val onClickBalance: () -> Unit,
): BaseListAdapter() {

    init {
        submitList(listOf(Item.Skeleton))
    }

    override fun createHolder(
        parent: ViewGroup,
        viewType: Int
    ): BaseListHolder<out BaseListItem> {
        return when(viewType) {
            Item.TYPE_BALANCE -> BalanceHolder(parent, onClickBalance)
            Item.TYPE_ACTIONS -> ActionsHolder(parent)
            Item.TYPE_TOKEN -> TokenHolder(parent)
            Item.TYPE_SPACE -> SpaceHolder(parent)
            Item.TYPE_SKELETON -> SkeletonHolder(parent)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }

}