package com.tonapps.tonkeeper.ui.screen.tronfees.list

import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.tronfees.TronFeesScreenType
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeperx.R
import uikit.widget.TextHeaderView

class HeaderHolder(parent: ViewGroup): Holder<Item.Header>(parent, R.layout.view_tron_fee_header) {

    private val headerView = findViewById<TextHeaderView>(R.id.header)

    override fun onBind(item: Item.Header) {
        headerView.title = if (item.screenType == TronFeesScreenType.DefaultFees) {
            if (item.onlyTrx) {
                getString(Localization.trx_balance)
            } else {
                getString(Localization.blockchain_fees)
            }
        } else {
            getString(Localization.insufficient_trx_balance)
        }
        headerView.desciption = if (item.onlyTrx) {
            getString(Localization.trx_balance_desc)
        } else if (item.screenType == TronFeesScreenType.DefaultFees) {
            getString(Localization.blockchain_fees_desc)
        } else {
            getString(Localization.insufficient_trx_balance_desc)
        }
    }

}