package com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import uikit.extensions.drawable

class PendingUnstakeHolder(
    parent: ViewGroup,
) : Holder<Item.PendingUnstake>(parent, R.layout.view_staking_pending_unstake) {

    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val subtitleView = findViewById<AppCompatTextView>(R.id.subtitle)
    private val balanceView = findViewById<AppCompatTextView>(R.id.balance)
    private val fiatView = findViewById<AppCompatTextView>(R.id.fiat)

    init {
        itemView.background = ListCell.Position.SINGLE.drawable(context)
    }

    override fun onBind(item: Item.PendingUnstake) {
        titleView.setText(item.titleRes)
        subtitleView.setText(item.subtitleRes)
        balanceView.text = if (item.hiddenBalance) {
            HIDDEN_BALANCE
        } else {
            item.balanceFormat.withCustomSymbol(context)
        }
        fiatView.text = if (item.hiddenBalance) {
            HIDDEN_BALANCE
        } else {
            item.fiatFormat.withCustomSymbol(context)
        }
    }
}
