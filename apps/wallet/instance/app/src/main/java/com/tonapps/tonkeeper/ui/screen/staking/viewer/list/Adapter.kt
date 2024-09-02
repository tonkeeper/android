package com.tonapps.tonkeeper.ui.screen.staking.viewer.list

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder.ActionsHolder
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder.BalanceHolder
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder.DetailsHolder
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder.LinksHolder
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder.SpaceHolder
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder.TokenHolder
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder.DescriptionHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem

class Adapter: BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when(viewType) {
            Item.TYPE_BALANCE -> BalanceHolder(parent)
            Item.TYPE_ACTIONS -> ActionsHolder(parent)
            Item.TYPE_DETAILS -> DetailsHolder(parent)
            Item.TYPE_LINKS -> LinksHolder(parent)
            Item.TYPE_TOKEN -> TokenHolder(parent)
            Item.TYPE_SPACE -> SpaceHolder(parent)
            Item.TYPE_DESCRIPTION -> DescriptionHolder(parent)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
}