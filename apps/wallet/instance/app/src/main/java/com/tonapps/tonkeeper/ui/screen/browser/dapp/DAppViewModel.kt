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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import uikit.extensions.collectFlow

class DAppViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val url: String,
    private val tonConnectRepository: TonConnectRepository,
    private val settingsRepository: SettingsRepository,
): BaseWalletVM(app) {

    private val _appFlow = MutableStateFlow<DConnectEntity?>(null)
    val appFlow = _appFlow.asStateFlow().filterNotNull()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            tonConnectRepository.getConnect(url, wallet)?.let {
                _appFlow.value = it
            }
        }
    }

    fun mute() {
        collectFlow(appFlow) { app ->
            tonConnectRepository.setPushEnabled(wallet, app, false, GooglePushService.requestToken())
        }
    }

    fun disconnect() {
        collectFlow(appFlow) { app ->
            tonConnectRepository.disconnect(wallet, app, settingsRepository.firebaseToken)
        }
    }

    suspend fun restoreConnection(): String {
        val reply = tonConnectRepository.autoConnect(wallet)
        return reply.toJSON().toString()
    }
}