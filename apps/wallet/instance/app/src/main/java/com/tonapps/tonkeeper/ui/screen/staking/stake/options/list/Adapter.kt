package com.tonapps.tonkeeper.ui.screen.staking.stake.options.list

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.staking.stake.options.list.holder.PoolHolder
import com.tonapps.tonkeeper.ui.screen.staking.stake.options.list.holder.SpaceHolder
import com.tonapps.tonkeeper.ui.screen.staking.stake.options.list.holder.TitleHolder
import com.tonapps.uikit.list.BaseListAdapter
import com.tonapps.uikit.list.BaseListHolder
import com.tonapps.uikit.list.BaseListItem
import com.tonapps.wallet.data.staking.entities.PoolInfoEntity

class Adapter(
    private val onClick: (PoolInfoEntity) -> Unit
): BaseListAdapter() {
    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when(viewType) {
            Item.TYPE_TITLE -> TitleHolder(parent)
            Item.TYPE_SPACE -> SpaceHolder(parent)
            Item.TYPE_POOL -> PoolHolder(parent, onClick)
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
}