package com.tonapps.tonkeeper.ui.screen.wallet.main.list.holder

import android.view.View
import android.view.ViewGroup
import com.tonapps.tonkeeper.koin.settingsRepository
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeperx.R

class SetupTitleHolder(parent: ViewGroup): Holder<Item.SetupTitle>(parent, R.layout.view_wallet_setup_title) {

    private val settingsRepository = context.settingsRepository

    private val doneButton = findViewById<View>(R.id.done)

    override fun onBind(item: Item.SetupTitle) {
        doneButton.visibility = if (item.showDone) View.VISIBLE else View.GONE
        doneButton.setOnClickListener {
            settingsRepository?.setupHide(item.walletId)
        }
    }

}