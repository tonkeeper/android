package com.tonapps.tonkeeper.fragment.settings.accounts.popup

import android.content.Context
import android.view.View
import com.tonapps.wallet.localization.Localization
import com.tonapps.tonkeeper.dialog.LogoutDialog
import com.tonapps.tonkeeper.ui.screen.name.RenameFragment
import com.tonapps.tonkeeper.fragment.settings.accounts.list.item.AccountsWalletItem
import com.tonapps.tonkeeper.popup.ActionSheet
import uikit.navigation.Navigation

class WalletActionsPopup(context: Context): ActionSheet(context) {

    private companion object {
        const val RENAME_ID = 1L
        const val DELETE_ID = 2L
    }

    private val logoutDialog: LogoutDialog by lazy { LogoutDialog(context) }

    init {
        addItem(RENAME_ID, Localization.rename, 0)
        addItem(DELETE_ID, Localization.log_out, 0)
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
        Navigation.from(context)?.add(RenameFragment.newInstance())
    }

    private fun showLogoutDialog(walletItem: AccountsWalletItem) {
        logoutDialog.show {
            com.tonapps.tonkeeper.App.instance.deleteWallet(walletItem.address)
        }
    }
}