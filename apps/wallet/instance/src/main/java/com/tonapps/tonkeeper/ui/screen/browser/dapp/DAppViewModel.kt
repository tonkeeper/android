package com.tonapps.tonkeeper.ui.screen.browser.dapp

import androidx.lifecycle.ViewModel
import com.tonapps.wallet.data.account.WalletRepository
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.tonconnect.TonConnectRepository
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import kotlinx.coroutines.flow.firstOrNull

class DAppViewModel(
    private val walletRepository: WalletRepository,
    private val tonConnectRepository: TonConnectRepository
): ViewModel() {

    suspend fun restoreConnection(url: String): String {
        val (wallet, _) = get(url)
        val reply = tonConnectRepository.autoConnect(wallet)
        return reply.toJSON().toString()
    }

    suspend fun disconnect(url: String) {
        val (_, app) = get(url)
        tonConnectRepository.disconnect(app)
    }

    private suspend fun get(url: String): Pair<WalletEntity, DAppEntity> {
        val wallet = walletRepository.activeWalletFlow.firstOrNull() ?: throw IllegalStateException("No active wallet")
        val app = tonConnectRepository.getApp(url, wallet) ?: throw IllegalStateException("No app")
        return wallet to app
    }
}