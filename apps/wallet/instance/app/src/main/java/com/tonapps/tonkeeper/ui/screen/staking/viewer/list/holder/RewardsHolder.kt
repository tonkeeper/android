package com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.ui.screen.staking.showStakingTooltip
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.localization.Localization
import uikit.extensions.drawable
import uikit.extensions.setMoonOnClickListener

class RewardsHolder(
    parent: ViewGroup,
) : Holder<Item.Rewards>(parent, R.layout.view_staking_rewards) {

    private val monthlyView = Card(findViewById(R.id.monthly), Localization.staking_rewards_monthly)
    private val yearlyView = Card(findViewById(R.id.yearly), Localization.staking_rewards_yearly)

    init {
        findViewById<View>(R.id.info).setMoonOnClickListener {
            showStakingTooltip(it, context.getString(Localization.staking_rewards_hint))
        }
    }

    override fun onBind(item: Item.Rewards) {
        monthlyView.bind(item.monthlyFormat, item.monthlyFiatFormat, item.hiddenBalance)
        yearlyView.bind(item.yearlyFormat, item.yearlyFiatFormat, item.hiddenBalance)
    }

    private class Card(view: View, titleRes: Int) {

        private val valueView = view.findViewById<AppCompatTextView>(R.id.value)
        private val fiatView = view.findViewById<AppCompatTextView>(R.id.fiat)

        init {
            view.background = ListCell.Position.SINGLE.drawable(view.context)
            view.findViewById<AppCompatTextView>(R.id.title).setText(titleRes)
        }

        fun bind(value: CharSequence, fiat: CharSequence, hiddenBalance: Boolean) {
            valueView.text = if (hiddenBalance) {
                HIDDEN_BALANCE
            } else {
                value.withCustomSymbol(valueView.context)
            }
            fiatView.text = if (hiddenBalance) {
                HIDDEN_BALANCE
            } else {
                fiat.withCustomSymbol(fiatView.context)
            }
        }
    }
}
