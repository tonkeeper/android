package com.tonkeeper.fragment.wallet.main.list.holder

import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonkeeper.R
import com.tonkeeper.api.shortAddress
import com.tonkeeper.extensions.copyToClipboard
import com.tonkeeper.extensions.copyWithToast
import com.tonkeeper.fragment.wallet.main.list.item.WalletDataItem
import uikit.navigation.Navigation.Companion.navigation

class WalletDataHolder(
    parent: ViewGroup
): WalletHolder<WalletDataItem>(parent, R.layout.view_wallet_data) {

    private val amountView = findViewById<AppCompatTextView>(R.id.amount)
    private val addressView = findViewById<AppCompatTextView>(R.id.address)

    override fun onBind(item: WalletDataItem) {
        amountView.text = item.amount

        addressView.text = item.address.shortAddress
        addressView.setOnClickListener {
            context.copyWithToast(item.address)
        }
    }
}
