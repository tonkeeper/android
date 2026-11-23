package com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.ListCell
import uikit.extensions.drawable

class RewardsSkeletonHolder(
    parent: ViewGroup,
) : Holder<Item.RewardsSkeleton>(parent, R.layout.view_staking_rewards_skeleton) {

    init {
        listOf(R.id.monthly, R.id.yearly).forEach { id ->
            findViewById<View>(id).background = ListCell.Position.SINGLE.drawable(context)
        }
    }

    override fun onBind(item: Item.RewardsSkeleton) = Unit
}
