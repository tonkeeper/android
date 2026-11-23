package com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.textPrimaryColor
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import uikit.extensions.drawable

class RewardHolder(
    parent: ViewGroup,
) : Holder<Item.Reward>(parent, R.layout.view_staking_reward) {

    private val apyView = findViewById<AppCompatTextView>(R.id.apy)
    private val amountView = findViewById<AppCompatTextView>(R.id.amount)
    private val dateView = findViewById<AppCompatTextView>(R.id.date)

    override fun onBind(item: Item.Reward) {
        itemView.background = item.position.drawable(context)
        apyView.text = item.apyFormat
        if (item.hiddenBalance) {
            amountView.text = HIDDEN_BALANCE
            amountView.setTextColor(context.textPrimaryColor)
        } else {
            amountView.text = item.amountFormat
            amountView.setTextColor(context.accentGreenColor)
        }
        dateView.text = item.dateFormat
    }
}
