package com.tonapps.tonkeeper.fragment.settings.accounts.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.api.shortAddress
import com.tonapps.tonkeeper.fragment.settings.accounts.list.item.AccountsWalletItem
import com.tonapps.tonkeeper.fragment.settings.accounts.popup.WalletActionsPopup
import uikit.extensions.drawable

class AccountsWalletHolder(parent: ViewGroup): AccountsHolder<AccountsWalletItem>(parent, R.layout.view_accounts_wallet) {

    private val actionsPopup: WalletActionsPopup by lazy {
        WalletActionsPopup(context)
    }

    private val nameView = findViewById<AppCompatTextView>(R.id.name)
    private val addressView = findViewById<AppCompatTextView>(R.id.address)
    private val actionsView = findViewById<View>(R.id.actions)

    override fun onBind(item: AccountsWalletItem) {
        itemView.background = item.position.drawable(context)
        nameView.text = item.nameOrDefault
        addressView.text = item.address.shortAddress
        actionsView.setOnClickListener { showPopup(it, item) }
    }

    private fun showPopup(view: View, item: AccountsWalletItem) {
        actionsPopup.show(view, item)
    }
}