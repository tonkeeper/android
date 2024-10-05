package com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.staking.stake.StakingScreen
import com.tonapps.tonkeeper.ui.screen.staking.unstake.UnStakeScreen
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.tonkeeperx.R
import uikit.navigation.Navigation

class ActionsHolder(
    parent: ViewGroup,
): Holder<Item.Actions>(parent, R.layout.view_staking_actions) {

    private val navigation = Navigation.from(context)
    private val plusView = findViewById<View>(R.id.plus)
    private val minusView = findViewById<View>(R.id.minus)

    override fun onBind(item: Item.Actions) {
        plusView.setOnClickListener {
            navigation?.add(StakingScreen.newInstance(item.wallet, item.poolAddress))
        }

        minusView.setOnClickListener {
            navigation?.add(UnStakeScreen.newInstance(item.wallet, item.poolAddress))
        }
    }


}