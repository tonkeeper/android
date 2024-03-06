package com.tonapps.tonkeeper.ui.screen.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.ui.screen.picker.list.Item
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.data.account.WalletRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PickerViewModel(
    private val walletRepository: WalletRepository,
    private val tokenRepository: TokenRepository,
    private val settings: SettingsRepository,
): ViewModel() {

    private val currency: WalletCurrency
        get() = settings.currency

    private val _itemsFlow = MutableStateFlow<List<Item>?>(null)
    val itemsFlow = _itemsFlow.asStateFlow().filterNotNull()

    private val job: Job

    init {
        job = walletRepository.walletsFlow.take(1).combine(walletRepository.activeWalletFlow.take(1)) { wallets, activeWallet ->
            setWallets(wallets, activeWallet)
        }.launchIn(viewModelScope)
    }

    private suspend fun setWallets(
        wallets: List<WalletEntity>,
        activeWallet: WalletEntity
    ) = withContext(Dispatchers.IO) {
        val balances = getBalances(wallets)
        val uiItems = mutableListOf<Item>()
        for ((index, wallet) in wallets.withIndex()) {
            val item = Item(
                accountId = wallet.accountId,
                walletId = wallet.id,
                walletLabel = wallet.label,
                walletType = wallet.type,
                selected = wallet.id == activeWallet.id,
                position = ListCell.getPosition(wallets.size, index),
                balance = balances[index].await(),
            )
            uiItems.add(item)
        }
        _itemsFlow.value = uiItems
    }

    fun setActiveWallet(id: Long) {
        job.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            walletRepository.setActiveWallet(id)
        }
    }

    private suspend fun getBalances(
        wallets: List<WalletEntity>
    ): List<Deferred<String>> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Deferred<String>>()
        for (wallet in wallets) {
            list.add(async { getBalance(wallet.accountId, wallet.testnet) })
        }
        list
    }

    private suspend fun getBalance(
        accountId: String,
        testnet: Boolean
    ): String {
        val currency = if (testnet) {
            WalletCurrency.TON
        } else {
            currency
        }
        val totalBalance = tokenRepository.getTotalBalances(currency, accountId, testnet)
        return CurrencyFormatter.formatFiat(currency.code, totalBalance)
    }
}