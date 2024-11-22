package com.tonapps.tonkeeper.ui.screen.wallet.picker

import android.app.Application
import android.util.Log
import androidx.collection.ArrayMap
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.core.entities.WalletExtendedEntity
import com.tonapps.tonkeeper.manager.assets.AssetsManager
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
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PickerViewModel(
    app: Application,
    private val mode: PickerMode,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val assetsManager: AssetsManager
): BaseWalletVM(app) {

    private val _editModeFlow = MutableStateFlow(false)
    val editModeFlow = _editModeFlow.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiItemsFlow = accountRepository.selectedWalletFlow.map { wallet ->
        val hiddenBalances = settingsRepository.hiddenBalances
        val wallets = getWallets()
        val currentWallet = if (mode is PickerMode.TonConnect) {
            wallets.first { it.id == mode.walletId }
        } else {
            wallet
        }

        flow {
            val balances = getCachedBalances(wallets)
            val walletIdFocus = (mode as? PickerMode.Focus)?.walletId ?: ""

            emit(Adapter.map(
                wallets = wallets,
                activeWallet = currentWallet,
                hiddenBalance = hiddenBalances,
                balances = balances,
                walletIdFocus = walletIdFocus
            ))

            if (!hiddenBalances) {
                for (chunkWallets in wallets.chunked(6)) {
                    balances += getRemoteBalances(chunkWallets)
                    emit(Adapter.map(
                        wallets = wallets,
                        activeWallet = currentWallet,
                        balances = balances,
                        walletIdFocus = walletIdFocus
                    ))
                }
            }

            if (walletIdFocus.isNotBlank()) {
                delay(1000)
                emit(Adapter.map(
                    wallets = wallets,
                    activeWallet = currentWallet,
                    balances = balances
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
        var wallets = accountRepository.getWallets()
        if (mode is PickerMode.TonConnect) {
            wallets = wallets.filter { it.isTonConnectSupported }
        }

        wallets.map {
            WalletExtendedEntity( it, settingsRepository.getWalletPrefs(it.id))
        }.sortedBy { it.index }.map { it.raw }
    }

    private suspend fun getRemoteBalance(
        wallet: WalletEntity
    ): CharSequence {
        val balance = assetsManager.getRemoteTotalBalance(
            wallet = wallet,
            currency = settingsRepository.currency,
            sorted = true
        ) ?: return getString(Localization.unknown)

        val currency = if (wallet.testnet) {
            WalletCurrency.TON.code
        } else {
            settingsRepository.currency.code
        }
        return CurrencyFormatter.formatFiat(currency, balance)
    }

    private fun getCachedBalance(
        wallet: WalletEntity
    ): CharSequence {
        val balance = assetsManager.getCachedTotalBalance(
            wallet = wallet,
            currency = settingsRepository.currency,
            sorted = true
        )
        val currency = if (wallet.testnet) {
            WalletCurrency.TON.code
        } else {
            settingsRepository.currency.code
        }
        return balance?.let {
            CurrencyFormatter.formatFiat(currency, it)
        } ?: getString(Localization.loading)
    }

    private suspend fun getRemoteBalances(
        wallets: List<WalletEntity>
    ): ArrayMap<String, CharSequence> = withContext(Dispatchers.IO) {
        val map = ArrayMap<String, Deferred<CharSequence>>()
        for (wallet in wallets) {
            map[wallet.id] = async { getRemoteBalance(wallet) }
        }
        ArrayMap<String, CharSequence>().apply {
            for ((id, deferred) in map) {
                put(id, deferred.await())
            }
        }
    }

    private fun getCachedBalances(
        wallets: List<WalletEntity>
    ): ArrayMap<String, CharSequence> {
        val map = ArrayMap<String, CharSequence>()
        for (wallet in wallets) {
            map[wallet.id] = getCachedBalance(wallet)
        }
        return map
    }

}