package com.tonapps.tonkeeper.ui.screen.token.viewer.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.ui.screen.token.viewer.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import uikit.widget.FrescoView

class BalanceHolder(parent: ViewGroup): Holder<Item.Balance>(parent, R.layout.view_token_balance) {

    private val balanceView = findViewById<AppCompatTextView>(R.id.balance)
    private val fiatBalanceView = findViewById<AppCompatTextView>(R.id.fiat_balance)
    private val iconView = findViewById<FrescoView>(R.id.icon)

    override fun onBind(item: Item.Balance) {
        balanceView.text = if (item.hiddenBalance) HIDDEN_BALANCE else item.balance.withCustomSymbol(context)
        fiatBalanceView.text = if (item.hiddenBalance) HIDDEN_BALANCE else item.fiat.withCustomSymbol(context)
        iconView.setImageURI(item.iconUri)
    }
}