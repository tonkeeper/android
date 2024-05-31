package com.tonapps.tonkeeper.ui.screen.wallet.main

import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull

internal class WalletTokens(
    private val tokenRepository: TokenRepository
) {

    private val _flow = MutableStateFlow<List<AccountTokenEntity>?>(null)
    val flow = _flow.asStateFlow().filterNotNull()

    private suspend fun loadCachedTokens(wallet: WalletEntity, currency: WalletCurrency) {
        _flow.value = tokenRepository.getLocal(currency, wallet.accountId, wallet.testnet)
    }

    private suspend fun loadRemoteTokens(wallet: WalletEntity, currency: WalletCurrency) {
        _flow.value = tokenRepository.getRemote(currency, wallet.accountId, wallet.testnet)
    }

    suspend fun load(wallet: WalletEntity, currency: WalletCurrency) {
        loadCachedTokens(wallet, currency)
        loadRemoteTokens(wallet, currency)
    }

}