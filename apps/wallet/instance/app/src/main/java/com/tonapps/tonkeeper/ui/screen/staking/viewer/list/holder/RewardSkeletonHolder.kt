package com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.drawable

class RewardSkeletonHolder(
    parent: ViewGroup,
) : Holder<Item.RewardSkeleton>(parent, R.layout.view_staking_reward_skeleton) {

    override fun onBind(item: Item.RewardSkeleton) {
        itemView.background = item.position.drawable(context)
    }
}
