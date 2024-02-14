package com.tonapps.tonkeeper.ui.screen.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.ui.screen.picker.list.WalletPickerItem
import com.tonapps.uikit.list.ListCell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import ton.wallet.Wallet

class WalletPickerViewModel: ViewModel() {

    private val _walletsFlow = MutableStateFlow<List<WalletPickerItem>?>(null)
    val walletsFlow = _walletsFlow.asStateFlow().filterNotNull()

    init {
        viewModelScope.launch {
            val currentWallet = App.walletManager.getWalletInfo() ?: return@launch

            val wallets = App.walletManager.getWallets()
            _walletsFlow.value = wrapWallets(wallets, currentWallet)
        }
    }

    fun setWallet(wallet: Wallet): Flow<Boolean> = flow {
        if (App.walletManager.getWalletInfo()?.id == wallet.id) {
            emit(false)
            return@flow
        }
        App.walletManager.setActiveWallet(wallet.id)
        emit(true)
    }.flowOn(Dispatchers.IO).take(1)

    private companion object {

        private fun wrapWallets(wallets: List<Wallet>, currentWallet: Wallet): List<WalletPickerItem> {
            val uiItems = mutableListOf<WalletPickerItem>()
            for ((index, wallet) in wallets.withIndex()) {
                uiItems.add(
                    WalletPickerItem(
                    wallet = wallet,
                    selected = wallet.id == currentWallet.id,
                    position = ListCell.getPosition(wallets.size, index)
                )
                )
            }
            return uiItems
        }
    }
}