package com.tonapps.tonkeeper.ui.screen.wallet.picker.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.init.InitArgs
import com.tonapps.tonkeeper.ui.screen.init.InitScreen
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.Item
import com.tonapps.tonkeeperx.R
import uikit.extensions.setMoonOnClickListener

class AddHolder(
    parent: ViewGroup
): Holder<Item.AddWallet>(parent, R.layout.view_wallet_add_item) {

    private val addButton = findViewById<View>(R.id.add)

    init {
        addButton.setMoonOnClickListener {
            navigation?.add(InitScreen.newInstance(InitArgs.Type.AddWallet))
        }
    }

    override fun onBind(item: Item.AddWallet) {

    }
}