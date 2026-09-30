package com.tonapps.tonkeeper.ui.screen.wallet.picker

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.portfolio.domain.WalletFiatBalanceInteractor
import com.tonapps.portfolio.wallet.CommonWallet
import com.tonapps.portfolio.wallet.WalletLookup
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.Adapter
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BALANCE_UNAVAILABLE = "—"

private data class PickerWallet(
    val entity: WalletEntity,
    val common: CommonWallet,
) {
    val id: String
        get() = entity.id
}

class PickerViewModel(
    app: Application,
    private val mode: PickerMode,
    private val accountRepository: AccountRepository,
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val walletLookup: WalletLookup,
    private val balanceInteractor: WalletFiatBalanceInteractor,
    private val settingsRepository: SettingsRepository,
): BaseWalletVM(app) {

    private val hiddenBalances = settingsRepository.hiddenBalances

    private val _walletIdFocusFlow = MutableStateFlow("")
    private val walletIdFocusFlow = _walletIdFocusFlow.asStateFlow().filterNotNull()

    private val _balancesFlow = MutableStateFlow<Map<String, CharSequence>>(emptyMap())
    private val balancesFlow = _balancesFlow.asStateFlow()

    private val _walletsFlow = MutableStateFlow<List<WalletEntity>?>(null)
    private val walletsFlow = _walletsFlow.asStateFlow().filterNotNull().filterNot { it.isEmpty() }

    private val _editModeFlow = MutableStateFlow(false)
    val editModeFlow = _editModeFlow.asStateFlow()

    private val activeWalletIdFlow: Flow<String> = when (mode) {
        is PickerMode.TonConnect -> flowOf(mode.walletId)
        else -> unifiedAccountRepository.selectedTonWalletFlow.filterNotNull().map { it.id }
    }

    val uiItemsFlow = combine(
        activeWalletIdFlow,
        walletsFlow,
        balancesFlow,
        walletIdFocusFlow,
    ) { activeWalletId, wallets, balances, walletIdFocus ->
        Adapter.map(
            context = context,
            wallets = wallets,
            activeWalletId = activeWalletId,
            balances = balances,
            hiddenBalance = hiddenBalances,
            walletIdFocus = walletIdFocus
        )
    }.flowOn(Dispatchers.IO)

    val isEditModeEnabled: Boolean
        get() = _editModeFlow.value

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val wallets = getWallets()
            if (!hiddenBalances) {
                loadCachedBalances(wallets)
            }
            _walletsFlow.value = wallets.map { it.entity }

            val walletIdFocus = (mode as? PickerMode.Focus)?.walletId ?: ""
            if (walletIdFocus.isNotBlank()) {
                delay(1000)
                _walletIdFocusFlow.value = walletIdFocus
            }

            if (!hiddenBalances) {
                fetchBalances(wallets)
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

    private suspend fun getWallets(): List<PickerWallet> = withContext(Dispatchers.IO) {
        val entities = unifiedAccountRepository.getTonWallets().associateBy { it.id }
        walletLookup.getAllWallets().mapNotNull { common ->
            val entity = entities[common.id] ?: return@mapNotNull null
            if (mode is PickerMode.TonConnect && !entity.isTonConnectSupported) {
                return@mapNotNull null
            }
            PickerWallet(entity, common)
        }
    }

    private suspend fun loadCachedBalances(wallets: List<PickerWallet>) {
        val cached = mutableMapOf<String, CharSequence>()
        for (wallet in wallets) {
            balanceInteractor.getCachedBalance(wallet.common)?.let { cached[wallet.id] = it }
        }
        _balancesFlow.update { it + cached }
    }

    private suspend fun fetchBalances(wallets: List<PickerWallet>) = coroutineScope {
        for (wallet in wallets) {
            launch {
                val balance = balanceInteractor.fetchBalance(wallet.common)
                if (balance == null) {
                    putBalanceIfAbsent(wallet.id, BALANCE_UNAVAILABLE)
                } else {
                    putBalance(wallet.id, balance)
                }
            }
        }
    }

    private fun putBalance(walletId: String, balance: CharSequence) {
        _balancesFlow.update { it + (walletId to balance) }
    }

    private fun putBalanceIfAbsent(walletId: String, balance: CharSequence) {
        _balancesFlow.update { balances ->
            if (balances.containsKey(walletId)) {
                balances
            } else {
                balances + (walletId to balance)
            }
        }
    }

}
