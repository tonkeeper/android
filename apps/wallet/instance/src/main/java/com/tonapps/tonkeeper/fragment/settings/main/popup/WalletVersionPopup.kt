package com.tonapps.tonkeeper.fragment.settings.main.popup

import android.content.Context
import androidx.collection.ArrayMap
import com.tonapps.uikit.icon.UIKitIcon
import ton.contract.WalletVersion
import com.tonapps.tonkeeper.popup.ActionSheet

class WalletVersionPopup(
    context: Context,
    current: WalletVersion,
    wallets: ArrayMap<WalletVersion, String>,
): ActionSheet(context) {

    init {
        val icon = getDrawable(UIKitIcon.ic_done_16)
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