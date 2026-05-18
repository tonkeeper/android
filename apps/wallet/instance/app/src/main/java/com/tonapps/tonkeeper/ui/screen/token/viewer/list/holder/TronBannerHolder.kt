package com.tonapps.tonkeeper.ui.screen.token.viewer.list.holder

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.deposit.screens.qr.QrAssetFragment
import com.tonapps.tonkeeper.ui.screen.token.viewer.list.Item
import com.tonapps.tonkeeper.ui.screen.tronfees.TronFeesScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.wallet.localization.Localization
import uikit.navigation.Navigation.Companion.navigation

class TronBannerHolder(parent: ViewGroup): Holder<Item.TronBanner>(parent, R.layout.view_token_battery_banner) {

    private val buttonView = findViewById<Button>(R.id.button)
    private val titleView = findViewById<AppCompatTextView>(R.id.title)
    private val subtitleView = findViewById<AppCompatTextView>(R.id.subtitle)
    private val batteryIconView = findViewById<View>(R.id.batteryIcon)
    private val trxIconView = findViewById<View>(R.id.trxIcon)

    override fun onBind(item: Item.TronBanner) {
        if (item.onlyTrx) {
            batteryIconView.visibility = View.GONE
            trxIconView.visibility =  View.VISIBLE
        } else {
            batteryIconView.visibility = View.VISIBLE
            trxIconView.visibility = View.GONE
        }
        titleView.text = if (item.onlyTrx) {
            context.getString(Localization.tron_not_enough_trx_for_fee_title)
        } else {
            context.getString(Localization.tron_not_enough_for_fee_title)
        }
        subtitleView.text = if (item.onlyTrx) {
            context.getString(Localization.tron_not_enough_trx_for_fee_desc, item.trxAmountFormat, item.trxBalanceFormat)
        } else {
            context.getString(Localization.tron_not_enough_for_fee_desc)
        }
        buttonView.text = if (item.onlyTrx) {
            context.getString(Localization.get_token, TokenEntity.TRX.symbol)
        } else {
            context.getString(Localization.top_up)
        }
        buttonView.setOnClickListener {
            if (item.onlyTrx) {
                context.navigation?.add(QrAssetFragment.newInstance(TokenEntity.TRX))
            } else {
                context.navigation?.add(TronFeesScreen.newInstance(item.wallet))
            }
        }
    }

}