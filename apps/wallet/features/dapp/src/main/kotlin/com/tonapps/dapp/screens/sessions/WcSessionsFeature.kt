package com.tonapps.dapp.screens.sessions

import androidx.core.net.toUri
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.mvi.MviRelay
import com.tonapps.wallet.data.dapps.DAppsRepository
import com.tonapps.wallet.data.dapps.entities.AppConnectEntity
import com.tonapps.wallet.data.dapps.entities.AppConnectWithDetails
import com.tonapps.wallet.data.dapps.entities.DappProvider
import com.tonapps.wallet.data.dapps.source.db.ConnectEntity
import com.tonapps.wallet.data.dapps.wc.WcRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
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
    private val tonConnectDisconnect: TonConnectDisconnectInteractor,
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
                DappProvider.TonConnect -> disconnectTonConnect(item.connect)
            }
            result
                .onSuccess { validations.emit(WcSessionsEvents.DisconnectSuccess) }
                .onFailure { validations.emit(WcSessionsEvents.DisconnectError) }
        }
    }

    private suspend fun disconnectTonConnect(connect: ConnectEntity): Result<String> {
        return try {
            closeTonConnectRow(connect)
            Result.success(connect.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun closeTonConnectRow(connect: ConnectEntity) {
        val type = AppConnectEntity.Type.entries.firstOrNull { it.value == connect.type }
        val appUrl = connect.appUrl?.toUri()
        val wallet = findWalletForRow(connect)
        if (wallet != null && type != null && appUrl != null) {
            tonConnectDisconnect.disconnectApp(wallet, appUrl, type)
        }
        dAppsRepository.deleteConnect(connect.id)
    }

    private suspend fun findWalletForRow(connect: ConnectEntity): WalletEntity? {
        val accountId = connect.accountId ?: return null
        val mode = connect.mode ?: TonNetwork.MAINNET.value
        val selected = unifiedAccountRepository.selectedTonWalletFlow.first()
        if (selected != null && selected.accountId == accountId && selected.network.value == mode) {
            return selected
        }
        val network = TonNetwork.from(mode) ?: return null
        return unifiedAccountRepository.getTonWalletByAccountId(accountId, network)
    }
}
