package com.tonapps.tonkeeper.ui.screen.browser.dapp

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.push.GooglePushService
import com.tonapps.wallet.data.push.PushManager
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.tonconnect.TonConnectRepository
import com.tonapps.wallet.data.tonconnect.entities.DConnectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.take
import uikit.extensions.collectFlow

class DAppViewModel(
    app: Application,
    private val url: String,
    private val accountRepository: AccountRepository,
    private val tonConnectRepository: TonConnectRepository,
    private val settingsRepository: SettingsRepository,
): BaseWalletVM(app) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getApp() = accountRepository.selectedWalletFlow.mapLatest {
        tonConnectRepository.getConnect(url, it)
    }.flowOn(Dispatchers.IO).take(1)

    fun mute() {
        combine(
            getApp().filterNotNull(),
            accountRepository.selectedWalletFlow.take(1),
        ) { app, wallet ->
            tonConnectRepository.setPushEnabled(wallet, app, false, GooglePushService.requestToken())
        }.launchIn(viewModelScope)
    }

    fun disconnect() {
        combine(
            accountRepository.selectedWalletFlow.take(1),
            getApp().filterNotNull(),
        ) { wallet, app ->
            tonConnectRepository.disconnect(wallet, app, settingsRepository.firebaseToken)
        }.launchIn(viewModelScope)
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