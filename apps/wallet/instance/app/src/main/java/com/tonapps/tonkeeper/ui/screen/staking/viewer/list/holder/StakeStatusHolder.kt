package com.tonapps.tonkeeper.ui.screen.staking.viewer.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.ui.screen.staking.viewer.list.Item
import com.tonapps.tonkeeper.ui.screen.staking.withdraw.StakeWithdrawScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import uikit.extensions.drawable
import uikit.extensions.setMoonOnClickListener
import uikit.navigation.Navigation

class StakeStatusHolder(
    parent: ViewGroup,
) : Holder<Item.StakeStatus>(parent, R.layout.view_staking_status) {

    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val subtitleView = findViewById<AppCompatTextView>(R.id.subtitle)
    private val balanceView = findViewById<AppCompatTextView>(R.id.balance)
    private val fiatView = findViewById<AppCompatTextView>(R.id.fiat)
    private val navigation = Navigation.from(context)

    init {
        itemView.background = ListCell.Position.SINGLE.drawable(context)
    }

    override fun onBind(item: Item.StakeStatus) {
        val collectTarget = item.collectTarget
        if (collectTarget != null) {
            itemView.setMoonOnClickListener {
                navigation?.add(StakeWithdrawScreen.newInstance(collectTarget.wallet, collectTarget.poolAddress))
            }
        } else {
            itemView.setOnClickListener(null)
            itemView.isClickable = false
        }
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
