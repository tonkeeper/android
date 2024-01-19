package com.tonkeeper.fragment.settings.accounts

import com.tonapps.tonkeeperx.R
import com.tonkeeper.fragment.settings.accounts.list.item.AccountsActionItem
import com.tonkeeper.fragment.settings.accounts.list.item.AccountsItem
import com.tonkeeper.fragment.settings.accounts.list.item.AccountsWalletItem
import ton.wallet.Wallet
import uikit.list.ListCell
import uikit.mvi.UiState

data class AccountsScreenState(
    val emptyWallets: Boolean = false,
    val wallets: List<Wallet> = emptyList()
): UiState() {

    fun getItems(): List<AccountsItem> {
        val items = mutableListOf<AccountsItem>()

        if (wallets.isNotEmpty()) {
            for ((index, wallet) in wallets.withIndex()) {
                val position = ListCell.getPosition(wallets.size, index)
                items.add(AccountsWalletItem(wallet, position))
            }
            items.add(
                AccountsActionItem(
                    id = AccountsActionItem.NEW_WALLET_ID,
                    titleRes = R.string.set_up_wallet,
                    iconRes = R.drawable.ic_plus_circle_28,
                    position = ListCell.Position.SINGLE
                )
            )
        }

        return items
    }
}