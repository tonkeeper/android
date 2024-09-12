package com.tonapps.tonkeeper.ui.screen.wallet.picker

import android.app.Application
import android.util.Log
import androidx.collection.ArrayMap
import com.tonapps.icu.Coins.Companion.sumOf
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.core.entities.AssetsEntity.Companion.sort
import com.tonapps.tonkeeper.core.entities.WalletExtendedEntity
import com.tonapps.tonkeeper.manager.AssetsManager
import com.tonapps.tonkeeper.manager.BalancesManager
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
    private val walletIdFocus: String,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val assetsManager: AssetsManager,
    private val balancesManager: BalancesManager,
): BaseWalletVM(app) {

    private val _editModeFlow = MutableStateFlow(false)
    val editModeFlow = _editModeFlow.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiItemsFlow = accountRepository.selectedWalletFlow.map { wallet ->
        val hiddenBalances = settingsRepository.hiddenBalances
        val wallets = getWallets()

        flow {
            val balances = balancesManager.get(wallets.map { it.id })
            val nonCachedWallets = wallets.filter { it.id !in balances.keys }

            emit(Adapter.map(
                wallets = wallets,
                activeWallet = wallet,
                hiddenBalance = hiddenBalances,
                balances = balances,
                walletIdFocus = walletIdFocus
            ))

            if (!hiddenBalances && nonCachedWallets.isNotEmpty()) {
                for (chunkWallets in wallets.chunked(5)) {
                    balances += getBalances(chunkWallets).also {
                        balancesManager.set(it)
                    }
                    emit(Adapter.map(
                        wallets = wallets,
                        activeWallet = wallet,
                        balances = balances,
                        walletIdFocus = walletIdFocus
                    ))
                }
            }

            if (walletIdFocus.isNotBlank()) {
                delay(1000)
                emit(Adapter.map(
                    wallets = wallets,
                    activeWallet = wallet,
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
    ): ArrayMap<String, CharSequence> = withContext(Dispatchers.IO) {
        val map = ArrayMap<String, Deferred<CharSequence>>()
        for (wallet in wallets) {
            map[wallet.id] = async { getBalance(wallet) }
        }
        ArrayMap<String, CharSequence>().apply {
            for ((id, deferred) in map) {
                put(id, deferred.await())
            }
        }
    }

}