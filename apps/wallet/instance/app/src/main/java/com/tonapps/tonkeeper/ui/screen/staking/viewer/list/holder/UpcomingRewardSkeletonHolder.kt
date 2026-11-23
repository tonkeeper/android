package com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.ListCell
import uikit.extensions.drawable

class UpcomingRewardSkeletonHolder(
    parent: ViewGroup,
) : Holder<Item.UpcomingRewardSkeleton>(parent, R.layout.view_staking_upcoming_reward_skeleton) {

    init {
        itemView.background = ListCell.Position.SINGLE.drawable(context)
    }

    override fun onBind(item: Item.UpcomingRewardSkeleton) = Unit
}
