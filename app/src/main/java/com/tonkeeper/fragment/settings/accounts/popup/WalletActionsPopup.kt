package com.tonkeeper.fragment.settings.accounts.popup

import android.content.Context
import android.view.View
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.dialog.LogoutDialog
import com.tonkeeper.dialog.WalletRenameDialog
import com.tonkeeper.fragment.settings.accounts.list.item.AccountsWalletItem
import uikit.popup.ActionSheet

class WalletActionsPopup(context: Context): ActionSheet(context) {

    private companion object {
        const val RENAME_ID = 1L
        const val DELETE_ID = 2L
    }

    private val renameDialog: WalletRenameDialog by lazy { WalletRenameDialog(context) }
    private val logoutDialog: LogoutDialog by lazy { LogoutDialog(context) }

    init {
        addItem(RENAME_ID, R.string.rename, 0)
        addItem(DELETE_ID, R.string.log_out, 0)
    }

    fun show(
        view: View,
        walletItem: AccountsWalletItem
    ) {
        doOnItemClick = { item ->
            when (item.id) {
                RENAME_ID -> showRenameDialog(walletItem)
                DELETE_ID -> showLogoutDialog(walletItem)
            }
        }
        super.show(view)
    }

    private fun showRenameDialog(walletItem: AccountsWalletItem) {
        renameDialog.show(walletItem.name, walletItem.address)
    }

    private fun showLogoutDialog(walletItem: AccountsWalletItem) {
        logoutDialog.show {
            App.instance.deleteWallet(walletItem.address)
        }
    }
}