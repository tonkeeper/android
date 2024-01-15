package com.tonkeeper.fragment.settings.main.popup

import android.content.Context
import androidx.collection.ArrayMap
import ton.contract.WalletVersion
import uikit.popup.ActionSheet

class WalletVersionPopup(
    context: Context,
    current: WalletVersion,
    wallets: ArrayMap<WalletVersion, String>,
): ActionSheet(context) {

    init {
        val icon = getDrawable(uikit.R.drawable.ic_done_16)
        for ((v, address) in wallets) {
            addItem(
                id = v.id.toLong(),
                title = v.name,
                subtitle = address,
                icon = if (v == current) icon else null
            )
        }
    }
}