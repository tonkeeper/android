package com.tonapps.tonkeeper.ui.screen.browser.dapp

import android.app.Application
import androidx.lifecycle.ViewModel
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.tonconnect.TonConnectRepository
import com.tonapps.wallet.data.tonconnect.entities.DConnectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.take
import uikit.extensions.collectFlow

class DAppViewModel(
    app: Application,
    private val url: String,
    private val accountRepository: AccountRepository,
    private val tonConnectRepository: TonConnectRepository
): BaseWalletVM(app) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getApp() = accountRepository.selectedWalletFlow.mapLatest {
        tonConnectRepository.getConnect(url, it)
    }.flowOn(Dispatchers.IO).take(1)

    fun mute() {
        collectFlow(getApp().filterNotNull()) { app ->
            tonConnectRepository.setPushEnabled(app, false)
        }
    }

    fun disconnect() {
        collectFlow(getApp().filterNotNull()) { app ->
            tonConnectRepository.disconnect(app)
        }
    }

    suspend fun restoreConnection(url: String): String {
        val (wallet, _) = get(url)
        val reply = tonConnectRepository.autoConnect(wallet)
        return reply.toJSON().toString()
    }

    private suspend fun get(url: String): Pair<WalletEntity, DConnectEntity> {
        val wallet = accountRepository.selectedWalletFlow.firstOrNull() ?: throw IllegalStateException("No active wallet")
        val app = tonConnectRepository.getConnect(url, wallet) ?: throw IllegalStateException("No app")
        return wallet to app
    }
}