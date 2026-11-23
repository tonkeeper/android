package com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.textPrimaryColor
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import uikit.extensions.drawable

class UpcomingRewardHolder(
    parent: ViewGroup,
) : Holder<Item.UpcomingReward>(parent, R.layout.view_staking_upcoming_reward) {

    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val subtitleView = findViewById<AppCompatTextView>(R.id.subtitle)
    private val amountView = findViewById<AppCompatTextView>(R.id.amount)

    init {
        itemView.background = ListCell.Position.SINGLE.drawable(context)
    }

    override fun onBind(item: Item.UpcomingReward) {
        titleView.setText(item.titleRes)
        subtitleView.text = item.subtitle
        if (item.hiddenBalance) {
            amountView.text = HIDDEN_BALANCE
            amountView.setTextColor(context.textPrimaryColor)
        } else {
            amountView.text = item.amountFormat
            amountView.setTextColor(context.accentGreenColor)
        }
    }
}
