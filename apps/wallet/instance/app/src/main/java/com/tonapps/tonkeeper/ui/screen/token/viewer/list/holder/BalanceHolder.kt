package com.tonapps.tonkeeper.ui.screen.token.viewer.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.icu.CurrencyFormatter.withCustomSymbol
import com.tonapps.tonkeeper.ui.screen.token.viewer.list.Item
import com.tonapps.tonkeeper.ui.screen.tronfees.TronFeesScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.data.core.HIDDEN_BALANCE
import com.tonapps.wallet.localization.Plurals
import uikit.navigation.Navigation.Companion.navigation
import uikit.widget.AsyncImageView

class BalanceHolder(parent: ViewGroup): Holder<Item.Balance>(parent, R.layout.view_token_balance) {

    private val balanceView = findViewById<AppCompatTextView>(R.id.balance)
    private val fiatBalanceView = findViewById<AppCompatTextView>(R.id.fiat_balance)
    private val iconView = findViewById<AsyncImageView>(R.id.icon)
    private val networkIconView = findViewById<AsyncImageView>(R.id.network_icon)
    private val availableTransfersContainerView = findViewById<View>(R.id.available_transfers_container)
    private val availableTransfersView = findViewById<AppCompatTextView>(R.id.available_transfers)

    override fun onBind(item: Item.Balance) {
        balanceView.text = if (item.hiddenBalance) HIDDEN_BALANCE else item.balance.withCustomSymbol(context)
        fiatBalanceView.text = if (item.hiddenBalance) HIDDEN_BALANCE else item.fiat.withCustomSymbol(context)
        iconView.setImageURI(item.iconUri)
        networkIconView.setLocalRes(item.networkIconRes)
        networkIconView.visibility = if (item.showNetwork) View.VISIBLE else View.GONE

        if (item.availableTransfers != null && item.availableTransfers > 0) {
            availableTransfersContainerView.visibility = View.VISIBLE
            availableTransfersView.text = context.resources.getQuantityString(
                Plurals.transfers_available,
                item.availableTransfers,
                item.availableTransfers
            )
            availableTransfersContainerView.setOnClickListener {
                context.navigation?.add(TronFeesScreen.newInstance(item.wallet))
            }
        } else {
            availableTransfersContainerView.visibility = View.GONE
        }
    }
}