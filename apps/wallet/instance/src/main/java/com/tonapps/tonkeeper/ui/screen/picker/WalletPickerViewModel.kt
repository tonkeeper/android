package com.tonapps.tonkeeper.ui.screen.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.App
import com.tonapps.tonkeeper.api.account.AccountRepository
import com.tonapps.tonkeeper.core.Coin
import com.tonapps.tonkeeper.core.currency.ton
import com.tonapps.tonkeeper.ui.screen.picker.list.WalletPickerItem
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.core.Currency
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
import ton.wallet.WalletManager

class WalletPickerViewModel(
    private val walletManager: WalletManager,
    private val accountRepository: AccountRepository,
): ViewModel() {

    private val currency: Currency
        get() = App.settings.currency

    private val _walletsFlow = MutableStateFlow<List<WalletPickerItem>?>(null)
    val walletsFlow = _walletsFlow.asStateFlow().filterNotNull()

    init {
        viewModelScope.launch {
            val currentWallet = walletManager.getWalletInfo() ?: return@launch

            val wallets = walletManager.getWallets()
            val uiItems = mutableListOf<WalletPickerItem>()
            for ((index, wallet) in wallets.withIndex()) {
                val item = WalletPickerItem(
                    wallet = wallet,
                    selected = wallet.id == currentWallet.id,
                    position = ListCell.getPosition(wallets.size, index),
                    balance = getBalance(wallet) ?: "..."
                )
                uiItems.add(item)
            }
            _walletsFlow.value = uiItems.toList()
        }
    }

    private suspend fun getBalance(wallet: Wallet): String? {
        val account = accountRepository.get(wallet.accountId, wallet.testnet)?.data ?: return null
        val balance = Coin.toCoins(account.balance)
        val tonInCurrency = wallet.ton(balance).convert(currency.code)
        return CurrencyFormatter.formatFiat(currency.code, tonInCurrency)
    }

    fun setWallet(wallet: Wallet): Flow<Boolean> = flow {
        if (walletManager.getWalletInfo()?.id == wallet.id) {
            emit(false)
            return@flow
        }
        walletManager.setActiveWallet(wallet.id)
        emit(true)
    }.flowOn(Dispatchers.IO).take(1)
}