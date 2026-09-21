package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeper.ui.screen.wallet.manage.TokensManageScreen
import com.tonapps.tonkeeperx.R
import com.tonapps.trading.AssetsFragment

class AssetsHeaderHolder(parent: ViewGroup): Holder<Item.AssetsHeader>(parent, R.layout.view_wallet_assets_header) {

    private val cryptoView = findViewById<View>(R.id.crypto)
    private val manageView = findViewById<View>(R.id.manage)

    override fun onBind(item: Item.AssetsHeader) {
        cryptoView.setOnClickListener {
            navigation?.add(AssetsFragment.newInstance())
        }
        manageView.setOnClickListener {
            navigation?.add(TokensManageScreen.newInstance(item.wallet))
        }
    }

}
