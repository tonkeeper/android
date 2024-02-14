package com.tonapps.tonkeeper.fragment.settings.accounts

import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeperx.R
import com.tonapps.tonkeeper.fragment.settings.accounts.list.item.AccountsActionItem
import com.tonapps.tonkeeper.fragment.settings.accounts.list.item.AccountsItem
import com.tonapps.tonkeeper.fragment.settings.accounts.list.item.AccountsWalletItem
import ton.wallet.Wallet
import uikit.mvi.UiState

data class AccountsScreenState(
    val emptyWallets: Boolean = false,
    val wallets: List<Wallet> = emptyList()
): UiState() {

    fun getItems(): List<AccountsItem> {
        val items = mutableListOf<AccountsItem>()

        if (wallets.isNotEmpty()) {
            for ((index, wallet) in wallets.withIndex()) {
                val position = com.tonapps.uikit.list.ListCell.getPosition(wallets.size, index)
                items.add(AccountsWalletItem(wallet, position))
            }
            items.add(
                AccountsActionItem(
                    id = AccountsActionItem.NEW_WALLET_ID,
                    titleRes = Localization.set_up_wallet,
                    iconRes = R.drawable.ic_plus_circle_28,
                    position = com.tonapps.uikit.list.ListCell.Position.SINGLE
                )
            )
        }

        return items
    }
}