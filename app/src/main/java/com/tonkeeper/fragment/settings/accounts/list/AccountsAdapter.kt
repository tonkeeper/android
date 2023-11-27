package com.tonkeeper.fragment.settings.accounts.list

import android.view.ViewGroup
import com.tonkeeper.fragment.settings.accounts.list.holder.AccountsActionHolder
import com.tonkeeper.fragment.settings.accounts.list.holder.AccountsWalletHolder
import com.tonkeeper.fragment.settings.accounts.list.item.AccountsItem
import uikit.list.BaseListHolder
import uikit.list.BaseListItem
import uikit.list.BaseListAdapter

class AccountsAdapter: BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): BaseListHolder<out BaseListItem> {
        return when (viewType) {
            AccountsItem.TYPE_WALLET -> AccountsWalletHolder(parent)
            AccountsItem.TYPE_ACTION -> AccountsActionHolder(parent)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }
}
