package com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.staking.rewards.StakingRewardsHistoryScreen
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.localization.Localization
import uikit.extensions.setMoonOnClickListener
import uikit.navigation.Navigation

class RewardsHistoryTitleHolder(
    parent: ViewGroup,
) : Holder<Item.RewardsHistoryTitle>(parent, R.layout.view_staking_rewards_history_title) {

    private val totalView = findViewById<AppCompatTextView>(R.id.total)

    override fun onBind(item: Item.RewardsHistoryTitle) {
        itemView.setMoonOnClickListener {
            Navigation.from(context)?.add(StakingRewardsHistoryScreen.newInstance(item.wallet))
        }
        val total = if (item.hiddenBalance) {
            HIDDEN_BALANCE
        } else {
            item.totalFormat
        }
        totalView.text = context.getString(Localization.total, total)
    }
}
