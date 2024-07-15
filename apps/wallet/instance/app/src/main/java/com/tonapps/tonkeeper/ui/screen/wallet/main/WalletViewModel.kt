package com.tonapps.tonkeeper.ui.screen.wallet.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonapps.network.NetworkMonitor
import com.tonapps.tonkeeper.extensions.hasPushPermission
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item.Status
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.NotificationEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEvent
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.core.ScreenCacheSource
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.push.PushManager
import com.tonapps.wallet.data.push.entities.AppPushEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.tonconnect.TonConnectRepository
import com.tonapps.wallet.data.tonconnect.entities.DAppEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.extensions.collectFlow
import uikit.extensions.context

class WalletViewModel(
    app: Application,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val tokenRepository: TokenRepository,
    private val api: API,
    private val networkMonitor: NetworkMonitor,
    private val pushManager: PushManager,
    private val tonConnectRepository: TonConnectRepository,
    private val screenCacheSource: ScreenCacheSource,
    private val backupRepository: BackupRepository
): AndroidViewModel(app) {

    private val alertNotificationsFlow = MutableStateFlow<List<NotificationEntity>>(emptyList())

    private val _uiLabelFlow = MutableStateFlow<Wallet.Label?>(null)
    val uiLabelFlow = _uiLabelFlow.asStateFlow()

    private val _lastLtFlow = MutableStateFlow(0L)
    private val _statusFlow = MutableStateFlow(Status.Updating)
    private val statusFlow = combine(
        networkMonitor.isOnlineFlow,
        _statusFlow
    ) { isOnline, status ->
        if (!isOnline) {
            Status.NoInternet
        } else {
            status
        }
    }.distinctUntilChanged()

    private val _stateMainFlow = MutableStateFlow<State.Main?>(null)
    private val stateMainFlow = _stateMainFlow.asStateFlow().filterNotNull()

    private val _stateSettingsFlow = combine(
        settingsRepository.hiddenBalancesFlow,
        api.configFlow,
        statusFlow
    ) { hiddenBalance, config, status ->
        State.Settings(hiddenBalance, config, status)
    }.distinctUntilChanged()

    private val _uiItemsFlow = MutableStateFlow<List<Item>?>(null)
    val uiItemsFlow = _uiItemsFlow.asStateFlow().filterNotNull()

    init {
        collectFlow(accountRepository.realtimeEventsFlow) { event ->
            if (event is WalletEvent.Boc) {
                setStatus(Status.SendingTransaction)
            } else if (event is WalletEvent.Transaction) {
                setStatus(Status.TransactionConfirmed)
                delay(2000)
                setStatus(Status.Default)
                _lastLtFlow.value = event.lt
            }
        }

        combine(
            accountRepository.selectedWalletFlow,
            settingsRepository.currencyFlow,
            backupRepository.stream,
        ) { wallet, currency, backups ->
            _uiLabelFlow.value = wallet.label

            val hasBackup = backups.indexOfFirst { it.walletId == wallet.id } > -1

            val localTokens = getLocalTokens(getCurrency(wallet, currency), wallet)
            if (localTokens != null) {
                _stateMainFlow.value = State.Main(wallet, localTokens, hasBackup)
            }

            val remoteTokens = getRemoteTokens(getCurrency(wallet, currency), wallet)
            if (remoteTokens != null) {
                _stateMainFlow.value = State.Main(wallet, remoteTokens, hasBackup)
            }
        }.launchIn(viewModelScope)

        combine(
            stateMainFlow,
            alertNotificationsFlow,
            pushManager.dAppPushFlow,
            _stateSettingsFlow,
        ) { state, alerts, dAppNotifications, settings ->
            val status = if (settings.status == Status.NoInternet) {
                settings.status
            } else if (settings.status != Status.SendingTransaction && settings.status != Status.TransactionConfirmed) {
                state.status
            } else {
                settings.status
            }

            val dAppEvents = dAppNotifications ?: emptyList()
            val apps = getApps(state.wallet, dAppEvents)

            val uiItems = state.uiItems(
                hiddenBalance = settings.hiddenBalance,
                status = status,
                config = settings.config,
                alerts = alerts,
                dAppNotifications = State.DAppNotifications(dAppEvents, apps),
                biometryEnabled = settingsRepository.biometric,
                push = context.hasPushPermission() && settingsRepository.getPushWallet(state.wallet.id)
            )
            _uiItemsFlow.value = uiItems
            setCached(state.wallet, uiItems)
        }.launchIn(viewModelScope)

        loadAlertNotifications()
    }

    private fun loadAlertNotifications() {
        viewModelScope.launch(Dispatchers.IO) {
            alertNotificationsFlow.value = api.getAlertNotifications()
        }
    }

    fun nextWallet() {
        viewModelScope.launch {
            val wallets = accountRepository.getWallets()
            val activeWallet = accountRepository.selectedWalletFlow.firstOrNull() ?: return@launch
            val index = wallets.indexOf(activeWallet)
            val nextIndex = if (index == wallets.size - 1) 0 else index + 1
            accountRepository.setSelectedWallet(wallets[nextIndex].id)
        }
    }

    fun prevWallet() {
        viewModelScope.launch {
            val wallets = accountRepository.getWallets()
            val activeWallet = accountRepository.selectedWalletFlow.firstOrNull() ?: return@launch
            val index = wallets.indexOf(activeWallet)
            val prevIndex = if (index == 0) wallets.size - 1 else index - 1
            accountRepository.setSelectedWallet(wallets[prevIndex].id)
        }
    }

    private fun setStatus(status: Status) {
        _statusFlow.tryEmit(status)
    }

    private suspend fun getLocalTokens(
        currency: WalletCurrency,
        wallet: WalletEntity
    ): State.Tokens? = withContext(Dispatchers.IO) {
        val tokens = tokenRepository.getLocal(currency, wallet.accountId, wallet.testnet).sortAndFilterTokens(wallet, settingsRepository)
        if (tokens.isEmpty()) {
            return@withContext null
        }
        State.Tokens(currency, tokens, fromCache = true)
    }

    private suspend fun getRemoteTokens(
        currency: WalletCurrency,
        wallet: WalletEntity
    ): State.Tokens? = withContext(Dispatchers.IO) {
        val tokens = tokenRepository.getRemote(currency, wallet.accountId, wallet.testnet).sortAndFilterTokens(wallet, settingsRepository)
        if (tokens.isEmpty()) {
            return@withContext null
        }
        State.Tokens(currency, tokens, fromCache = false)
    }

    private fun getApps(
        wallet: WalletEntity,
        events: List<AppPushEntity>
    ): List<DAppEntity> {
        if (events.isEmpty()) {
            return emptyList()
        }
        val dappUrls = events.map { it.dappUrl }
        return tonConnectRepository.getApps(dappUrls, wallet)
    }

    private fun setCached(wallet: WalletEntity, items: List<Item>) {
        screenCacheSource.set(CACHE_NAME, wallet.id, items)
    }

    companion object {
        private const val CACHE_NAME = "wallet"

        private fun getCurrency(
            wallet: WalletEntity,
            currency: WalletCurrency
        ): WalletCurrency {
            return if (wallet.testnet) WalletCurrency.TON else currency
        }

        fun ScreenCacheSource.getWalletScreen(wallet: WalletEntity): List<Item>? {
            try {
                val items: List<Item> = get(CACHE_NAME, wallet.id) { parcel ->
                    Item.createFromParcel(parcel)
                }.map {
                    if (it is Item.Balance) {
                        it.copy(status = Status.Updating)
                    } else {
                        it
                    }
                }
                if (items.isEmpty()) {
                    return null
                }
                return items
            } catch (e: Throwable) {
                return null
            }
        }
    }
}