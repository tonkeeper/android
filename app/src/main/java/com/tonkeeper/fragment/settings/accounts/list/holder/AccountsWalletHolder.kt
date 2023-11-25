package com.tonkeeper.fragment.settings.accounts.list.holder

import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import com.tonkeeper.R
import com.tonkeeper.api.shortAddress
import com.tonkeeper.fragment.settings.accounts.list.item.AccountsWalletItem
import com.tonkeeper.fragment.settings.accounts.popup.WalletActionsPopup
import uikit.drawable.CellBackgroundDrawable
import uikit.list.ListCell

class AccountsWalletHolder(parent: ViewGroup): AccountsHolder<AccountsWalletItem>(parent, R.layout.view_accounts_wallet) {

    private val actionsPopup: WalletActionsPopup by lazy {
        WalletActionsPopup(context)
    }

    private val nameView = findViewById<AppCompatTextView>(R.id.name)
    private val addressView = findViewById<AppCompatTextView>(R.id.address)
    private val actionsView = findViewById<View>(R.id.actions)

    override fun onBind(item: AccountsWalletItem) {
        itemView.background = CellBackgroundDrawable(context, item.position)
        nameView.text = item.nameOrDefault
        addressView.text = item.address.shortAddress
        actionsView.setOnClickListener { showPopup(it, item) }
    }

    private fun showPopup(view: View, item: AccountsWalletItem) {
        actionsPopup.show(view, item)
    }
}