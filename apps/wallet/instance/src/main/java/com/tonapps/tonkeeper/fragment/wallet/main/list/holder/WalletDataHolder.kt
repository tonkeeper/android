package com.tonapps.tonkeeper.fragment.wallet.main.list.holder

import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.api.shortAddress
import com.tonapps.tonkeeper.extensions.copyWithToast
import com.tonapps.tonkeeper.fragment.wallet.main.list.item.WalletDataItem
import com.tonapps.uikit.color.accentGreenColor
import com.tonapps.uikit.color.accentOrangeColor
import com.tonapps.uikit.color.accentPurpleColor
import com.tonapps.wallet.localization.Localization
import ton.wallet.WalletType
import uikit.extensions.withAlpha

class WalletDataHolder(
    parent: ViewGroup
): WalletHolder<WalletDataItem>(parent, R.layout.view_wallet_data) {

    private val balanceView = findViewById<AppCompatTextView>(R.id.wallet_balance)
    private val addressView = findViewById<AppCompatTextView>(R.id.wallet_address)
    private val typeView = findViewById<AppCompatTextView>(R.id.wallet_type)

    override fun onBind(item: WalletDataItem) {
        balanceView.text = item.amount

        addressView.text = item.address.shortAddress
        addressView.setOnClickListener {
            context.copyWithToast(item.address)
        }

        setWalletType(item.walletType)
    }

    private fun setWalletType(type: WalletType) {
        if (type == WalletType.Default) {
            typeView.visibility = View.GONE
            return
        }
        typeView.visibility = View.VISIBLE
        if (type == WalletType.Watch) {
            typeView.setText(Localization.watch_only)
            val color = context.accentOrangeColor
            typeView.backgroundTintList = ColorStateList.valueOf(color.withAlpha(.16f))
            typeView.setTextColor(color)
        } else if (type == WalletType.Testnet) {
            typeView.setText(Localization.testnet)
            val color = context.accentGreenColor
            typeView.backgroundTintList = ColorStateList.valueOf(color.withAlpha(.16f))
            typeView.setTextColor(color)
        } else if (type == WalletType.Signer) {
            typeView.setText(Localization.signer)
            val color = context.accentPurpleColor
            typeView.backgroundTintList = ColorStateList.valueOf(color.withAlpha(.16f))
            typeView.setTextColor(color)
        }
    }
}
