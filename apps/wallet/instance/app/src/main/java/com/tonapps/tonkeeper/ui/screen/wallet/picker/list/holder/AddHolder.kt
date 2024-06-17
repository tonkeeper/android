package com.tonapps.tonkeeper.ui.screen.wallet.picker.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeper.ui.screen.add.main.AddScreen
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.Item
import com.tonapps.tonkeeperx.R

class AddHolder(
    parent: ViewGroup
): Holder<Item.AddWallet>(parent, R.layout.view_wallet_add_item) {

    private val addButton = findViewById<View>(R.id.add)

    init {
        addButton.setOnClickListener {
            navigation?.add(AddScreen.newInstance())
        }
    }

    override fun onBind(item: Item.AddWallet) {

    }
}