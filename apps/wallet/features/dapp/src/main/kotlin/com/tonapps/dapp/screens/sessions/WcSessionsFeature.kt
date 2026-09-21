package com.tonapps.dapp.screens.sessions

import com.tonapps.mvi.AsyncViewModel
import com.tonapps.mvi.MviRelay
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.dapps.entities.AppConnectWithDetails
import com.tonapps.wallet.data.dapps.entities.DappProvider
import com.tonapps.wallet.data.dapps.wc.WcRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

sealed interface WcSessionsEvents {
    object DisconnectSuccess : WcSessionsEvents
    object DisconnectError : WcSessionsEvents
}

class WcSessionsFeature(
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val dAppsRepository: DAppsRepository,
    private val wcRepository: WcRepository,
) : AsyncViewModel() {

    private val validations = MviRelay<WcSessionsEvents>()
    val events = validations.events

    val apps = MutableStateFlow<List<AppConnectWithDetails>>(emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val walletScopedFlow = unifiedAccountRepository.selectedTonWalletFlow
        .filterNotNull()
        .flatMapLatest { wallet ->
            dAppsRepository.connectionsWithDetailsFlow(
                walletId = wallet.id,
                accountId = wallet.accountId,
                mode = wallet.network.value,
            )
        }

    init {
        walletScopedFlow
            .onEach { apps.emit(it) }
            .launchIn(bgScope)
    }

    fun disconnect(item: AppConnectWithDetails) {
        bgScope.launch {
            val result = when (item.connect.provider) {
                DappProvider.WalletConnect -> {
                    val topic = item.connect.topic
                    if (topic == null) {
                        Result.failure(IllegalStateException("WalletConnect row has no topic"))
                    } else {
                        wcRepository.disconnect(topic)
                    }
                }
                DappProvider.TonConnect -> {
                    if (dAppsRepository.deleteConnect(item.connect.id)) {
                        Result.success(item.connect.id)
                    } else {
                        Result.failure(IllegalStateException("TonConnect row not found"))
                    }
                }
            }
            result
                .onSuccess { validations.emit(WcSessionsEvents.DisconnectSuccess) }
                .onFailure { validations.emit(WcSessionsEvents.DisconnectError) }
        }
    }
}
