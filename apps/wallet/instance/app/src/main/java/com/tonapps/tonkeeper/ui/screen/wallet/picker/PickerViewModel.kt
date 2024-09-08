package com.tonapps.tonkeeper.ui.screen.wallet.picker

import android.app.Application
import android.util.Log
import com.tonapps.icu.Coins.Companion.sumOf
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.core.entities.AssetsEntity.Companion.sort
import com.tonapps.tonkeeper.core.entities.WalletExtendedEntity
import com.tonapps.tonkeeper.manager.AssetsManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.Adapter
import com.tonapps.tonkeeper.ui.screen.wallet.picker.list.Item
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
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PickerViewModel(
    app: Application,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val assetsManager: AssetsManager,
): BaseWalletVM(app) {

    private val _editModeFlow = MutableStateFlow(false)
    val editModeFlow = _editModeFlow.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiItemsFlow = accountRepository.selectedWalletFlow.map { wallet ->
        val hiddenBalances = settingsRepository.hiddenBalances
        val wallets = getWallets()

        flow {
            emit(Adapter.map(
                wallets = wallets,
                activeWallet = wallet,
                hiddenBalance = hiddenBalances
            ))

            if (!hiddenBalances) {
                val balances = getBalances(wallets).map { it }
                emit(Adapter.map(
                    wallets = wallets,
                    activeWallet = wallet,
                    balances = balances,
                    hiddenBalance = false
                ))
            }
        }
    }.flattenConcat().flowOn(Dispatchers.IO)

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
        accountRepository.getWallets().map {
            WalletExtendedEntity( it, settingsRepository.getWalletPrefs(it.id))
        }.sortedBy { it.index }.map { it.raw }
    }

    private suspend fun getAssets(
        wallet: WalletEntity
    ) = assetsManager.getAssets(wallet)?.sort(wallet, settingsRepository)

    private suspend fun getBalance(
        wallet: WalletEntity
    ): CharSequence {
        val assets = getAssets(wallet) ?: return getString(Localization.unknown)
        val balance = assets.map { it.fiat }.sumOf { it }
        val currency = if (wallet.testnet) {
            WalletCurrency.TON.code
        } else {
            settingsRepository.currency.code
        }
        return CurrencyFormatter.formatFiat(currency, balance)
    }

    private suspend fun getBalances(
        wallets: List<WalletEntity>
    ): List<CharSequence> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Deferred<CharSequence>>()
        for (wallet in wallets) {
            list.add(async { getBalance(wallet) })
        }
        list.map { it.await() }
    }

}