package com.tonkeeper.fragment.send.popup

import android.content.Context
import android.net.Uri
import com.tonkeeper.R
import com.tonkeeper.api.parsedBalance
import com.tonkeeper.api.shortAddress
import com.tonkeeper.core.Coin
import io.tonapi.models.JettonBalance
import io.tonapi.models.JettonPreview
import ton.SupportedTokens
import uikit.extensions.textWithLabel
import uikit.popup.ActionSheet

class SelectTokenPopup(context: Context): ActionSheet(context) {

    var jettons = listOf<JettonBalance>()
        set(value) {
            if (field != value) {
                field = value
                updateTokens()
            }
        }

    var selectedJetton: JettonBalance? = null
        set(value) {
            if (field != value) {
                field = value
                updateTokens()
            }
        }

    var doOnSelectJetton: ((JettonBalance) -> Unit)? = null

    init {
        doOnItemClick = { item ->
            val jetton = jettons[item.id.toInt()]
            doOnSelectJetton?.invoke(jetton)
        }
    }

    private fun updateTokens() {
        clearItems()
        for ((index, jetton) in jettons.withIndex()) {
            val info = jetton.jetton
            val format = Coin.format(value = jetton.parsedBalance, decimals = Coin.MAX_DECIMALS)
            val title = context.textWithLabel(info.symbol, format)
            val selected = jetton == selectedJetton
            addItem(
                id = index.toLong(),
                title = title,
                icon = if (selected) {
                    getDrawable(uikit.R.drawable.ic_done_16)
                } else {
                    null
                },
                imageUri = Uri.parse(info.image)
            )
        }
    }
}