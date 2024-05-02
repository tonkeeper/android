package com.tonapps.tonkeeper.fragment.jetton.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.api.getAddress
import com.tonapps.tonkeeper.extensions.sendCoin
import com.tonapps.tonkeeper.fragment.jetton.list.JettonItem
import com.tonapps.tonkeeper.ui.screen.qr.QRScreen
import com.tonapps.wallet.api.entity.TokenEntity
import com.tonapps.wallet.data.account.WalletType
import uikit.navigation.Navigation

class JettonActionsHolder(
    parent: ViewGroup
): JettonHolder<JettonItem.Actions>(parent, R.layout.view_jetton_actions) {

    private val navigation = Navigation.from(context)
    private val sendView = findViewById<View>(R.id.send)
    private val receiveView = findViewById<View>(R.id.receive)

    override fun onBind(item: JettonItem.Actions) {
        sendView.setOnClickListener { navigation?.sendCoin(
            jettonAddress = item.jetton.jetton.address,
        ) }

        sendView.visibility = if (item.walletType == WalletType.Watch) {
            View.GONE
        } else {
            View.VISIBLE
        }

        receiveView.setOnClickListener {
            val token = TokenEntity(item.jetton.jetton)
            navigation?.add(QRScreen.newInstance(item.wallet, token, item.walletType))
        }
    }

}