package com.tonkeeper.fragment.wallet.main.popup

import android.content.Context
import com.tonkeeper.R
import uikit.popup.ActionSheet

class NewWalletPopup(context: Context): ActionSheet(context) {

    private companion object {
        const val SET_UP_WALLET_ID = 1L
    }

    var doOnCreateWalletClick: (() -> Unit)? = null

    init {
        doOnItemClick = { item ->
            when (item.id) {
                SET_UP_WALLET_ID -> doOnCreateWalletClick?.invoke()
            }
        }
        addItem(SET_UP_WALLET_ID, R.string.set_up_wallet, uikit.R.drawable.ic_plus_alternate_16)
    }
}