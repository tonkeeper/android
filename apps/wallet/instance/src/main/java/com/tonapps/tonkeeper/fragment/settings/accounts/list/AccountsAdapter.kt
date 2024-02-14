package com.tonapps.tonkeeper.fragment.settings.accounts.list

import android.view.ViewGroup
import com.tonapps.tonkeeper.fragment.settings.accounts.list.holder.AccountsActionHolder
import com.tonapps.tonkeeper.fragment.settings.accounts.list.holder.AccountsWalletHolder
import com.tonapps.tonkeeper.fragment.settings.accounts.list.item.AccountsItem

class AccountsAdapter: com.tonapps.uikit.list.BaseListAdapter() {

    override fun createHolder(parent: ViewGroup, viewType: Int): com.tonapps.uikit.list.BaseListHolder<out com.tonapps.uikit.list.BaseListItem> {
        return when (viewType) {
            AccountsItem.TYPE_WALLET -> AccountsWalletHolder(parent)
            AccountsItem.TYPE_ACTION -> AccountsActionHolder(parent)
            else -> throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }
}
