package com.tonapps.tonkeeper.ui.screen.wallet.picker

import android.app.Application
import android.util.Log
import androidx.collection.ArrayMap
import androidx.lifecycle.viewModelScope
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.core.entities.WalletExtendedEntity
import com.tonapps.tonkeeper.manager.assets.AssetsManager
import com.tonapps.tonkeeper.manager.assets.WalletBalanceEntity
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.Adapter
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PickerViewModel(
    app: Application,
    private val mode: PickerMode,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val assetsManager: AssetsManager
): BaseWalletVM(app) {

    private val hiddenBalances = settingsRepository.hiddenBalances

    private val _walletIdFocusFlow = MutableStateFlow("")
    private val walletIdFocusFlow = _walletIdFocusFlow.asStateFlow().filterNotNull()

    private val _balancesFlow = MutableStateFlow<List<WalletBalanceEntity>>(emptyList())
    private val balancesFlow = _balancesFlow.asStateFlow()

    private val _walletsFlow = MutableStateFlow<List<WalletEntity>?>(null)
    private val walletsFlow = _walletsFlow.asStateFlow().filterNotNull().filterNot { it.isEmpty() }

    private val _editModeFlow = MutableStateFlow(false)
    val editModeFlow = _editModeFlow.asStateFlow()

    val uiItemsFlow = combine(
        accountRepository.selectedWalletFlow,
        walletsFlow,
        balancesFlow,
        walletIdFocusFlow,
    ) { currentWallet, wallets, balances, walletIdFocus ->
        Adapter.map(
            context = context,
            wallets = wallets,
            activeWallet = currentWallet,
            currency = settingsRepository.currency,
            balances = balances,
            hiddenBalance = hiddenBalances,
            walletIdFocus = walletIdFocus
        )
    }.flowOn(Dispatchers.IO)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val wallets = getWallets()
            if (!hiddenBalances) {
                loadCachedBalances(wallets)
            }
            _walletsFlow.value = wallets

            val walletIdFocus = (mode as? PickerMode.Focus)?.walletId ?: ""
            if (walletIdFocus.isNotBlank()) {
                delay(1000)
                _walletIdFocusFlow.value = walletIdFocus
            }

            if (!hiddenBalances) {
                loadRemoteBalances(wallets)
            }
        }
    }

    fun toggleEditMode() {
        _editModeFlow.value = !_editModeFlow.value
    }

    fun saveOrder(wallerIds: List<String>) {
        settingsRepository.setWalletsSort(wallerIds)
    }

    fun setWallet(wallet: WalletEntity) {
        accountRepository.safeSetSelectedWallet(wallet.id)
    }

    private suspend fun getWallets(): List<WalletEntity> = withContext(Dispatchers.IO) {
        var wallets = accountRepository.getWallets()
        if (mode is PickerMode.TonConnect) {
            wallets = wallets.filter { it.isTonConnectSupported }
        }

        wallets.map {
            WalletExtendedEntity( it, settingsRepository.getWalletPrefs(it.id))
        }.sortedBy { it.index }.map { it.raw }
    }

    private suspend fun loadCachedBalances(wallets: List<WalletEntity>) {
        loadBalances(wallets) { wallet ->
            assetsManager.getCachedTotalBalance(
                wallet = wallet,
                currency = settingsRepository.currency,
                sorted = true
            )
        }
    }

    private suspend fun loadRemoteBalances(wallets: List<WalletEntity>) {
        loadBalances(wallets) { wallet ->
            assetsManager.requestTotalBalance(
                wallet = wallet,
                currency = settingsRepository.currency,
                refresh = false,
                sorted = true
            )
        }
    }

    private suspend fun loadBalances(wallets: List<WalletEntity>, block: suspend (WalletEntity) -> Coins?) {
        val balances = _balancesFlow.value.toMutableList()
        for (wallet in wallets) {
            val balance = block(wallet) ?: continue

            balances.removeIf { it.accountId == wallet.id && it.testnet == wallet.testnet }
            balances.add(WalletBalanceEntity(
                accountId = wallet.accountId,
                testnet = wallet.testnet,
                balance = balance
            ))
        }

        _balancesFlow.value = balances.toList()
    }

}