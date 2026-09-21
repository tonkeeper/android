package com.tonapps.portfolio.wallet

import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class WalletLookup(
    private val accountRepository: AccountRepository,
    private val mcAccountRepository: McAccountRepository,
    private val settingsRepository: SettingsRepository,
) {

    val selectedWalletFlow: Flow<CommonWallet?> = accountRepository.selectedWalletFlow
        .map { findById(it.id) }
        .distinctUntilChanged { old, new -> old?.id == new?.id && old?.javaClass == new?.javaClass }

    suspend fun getAllWallets(): List<CommonWallet> {
        val mc = mcAccountRepository.getWallets().map { CommonWallet.Mc(it) }
        val legacy = accountRepository.getWallets().map { CommonWallet.Legacy(it) }
        return (mc + legacy).sortedBy { settingsRepository.getWalletSortIndex(it.id) ?: Int.MAX_VALUE }
    }

    suspend fun findById(id: String): CommonWallet? {
        mcAccountRepository.getWallet(id)?.let { return CommonWallet.Mc(it) }
        accountRepository.getWalletById(id)?.let { return CommonWallet.Legacy(it) }
        return null
    }

    suspend fun getSelectedWallet(): CommonWallet? {
        val id = accountRepository.getSelectedWalletId() ?: return null
        return findById(id)
    }
}
