package com.tonapps.tonkeeper.ui.screen.wallet.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeper.api.shortAddress
import com.tonapps.tonkeeper.ui.screen.wallet.list.Item
import com.tonapps.tonkeeperx.R
import com.tonapps.uikit.color.accentOrangeColor
import com.tonapps.uikit.color.iconSecondaryColor
import com.tonapps.uikit.color.stateList
import com.tonapps.wallet.localization.Localization
import com.tonapps.wallet.data.account.WalletType
import uikit.extensions.withAlpha
import uikit.widget.LoaderView

class BalanceHolder(parent: ViewGroup): Holder<Item.Balance>(parent, R.layout.view_wallet_data) {

    private val balanceView = itemView.findViewById<AppCompatTextView>(R.id.wallet_balance)
    private val walletAddressView = itemView.findViewById<AppCompatTextView>(R.id.wallet_address)
    private val walletLoaderView = itemView.findViewById<LoaderView>(R.id.wallet_loader)
    private val walletTypeView = itemView.findViewById<AppCompatTextView>(R.id.wallet_type)

    init {
        walletLoaderView.setColor(context.iconSecondaryColor)
        walletLoaderView.setTrackColor(context.iconSecondaryColor.withAlpha(.32f))
        walletTypeView.backgroundTintList = context.accentOrangeColor.withAlpha(.16f).stateList
    }

    override fun onBind(item: Item.Balance) {
        balanceView.text = item.balance
        setWalletType(item.walletType)
        setWalletState(item.status, item.address.shortAddress)
    }

    private fun setWalletState(state: Item.Status, shortAddress: String) {
        if (state == Item.Status.Updating) {
            walletLoaderView.visibility = View.VISIBLE
            walletAddressView.setText(Localization.updating)
        } else {
            walletLoaderView.visibility = View.GONE
            walletAddressView.text = shortAddress
        }
    }

    private fun setWalletType(type: WalletType) {
        if (type == WalletType.Default) {
            walletTypeView.visibility = View.GONE
            return
        }
        walletTypeView.visibility = View.VISIBLE
        if (type == WalletType.Watch) {
            walletTypeView.setText(Localization.watch_only)
        } else if (type == WalletType.Testnet) {
            walletTypeView.setText(Localization.testnet)
        } else if (type == WalletType.Signer) {
            walletTypeView.setText(Localization.signer)
        }
    }
}