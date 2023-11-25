package com.tonkeeper.fragment.wallet.main.popup

import android.content.Context
import android.view.View
import com.tonkeeper.App
import com.tonkeeper.R
import com.tonkeeper.api.shortAddress
import com.tonkeeper.api.userLikeAddress
import com.tonkeeper.fragment.settings.accounts.AccountsScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ton.wallet.Wallet
import ton.wallet.WalletInfo
import ton.wallet.WalletManager
import uikit.extensions.getDrawable
import uikit.navigation.Navigation.Companion.nav
import uikit.popup.ActionSheet

class WalletPickerPopup(
    private val scope: CoroutineScope,
    context: Context
): ActionSheet(context) {

    private companion object {
        const val SET_UP_WALLET_ID = -99L
    }

    private val walletManager: WalletManager
        get() = App.walletManager

    init {
        doOnItemClick = {
            if (it.id == SET_UP_WALLET_ID) {
                context.nav()?.add(AccountsScreen.newInstance())
            } else {
                setActiveWallet(it.id)
            }
        }
    }

    fun show(wallets: List<Wallet>, selectedWalletId: Long, view: View) {
        if (isShowing) {
            dismiss()
            return
        }

        for ((index, wallet) in wallets.withIndex()) {
            val title = if (wallet.name.isNullOrEmpty()) {
                "Wallet ${index + 1}"
            } else {
                wallet.name!!
            }

            val address = wallet.address.userLikeAddress.shortAddress
            val createDate = wallet.id
            if (createDate == selectedWalletId) {
                addItem(createDate, title, address, view.getDrawable(uikit.R.drawable.ic_done_16))
            } else {
                addItem(createDate, title, address, null)
            }
        }

        addItem(SET_UP_WALLET_ID, R.string.manage, uikit.R.drawable.ic_gear_16)

        show(view)
    }

    private fun setActiveWallet(createDate: Long) {
        scope.launch {
            val activeWallet = walletManager.getActiveWallet()
            if (activeWallet == createDate) {
                return@launch
            }
            walletManager.setActiveWallet(createDate)
            context.nav()?.init(true)
        }
    }
}