package com.tonapps.tonkeeper.ui.screen.wallet.main

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.tonapps.icu.Coins
import com.tonapps.network.NetworkMonitor
import com.tonapps.tonkeeper.billing.BillingManager
import com.tonapps.tonkeeper.core.entities.AssetsEntity
import com.tonapps.tonkeeper.core.entities.AssetsEntity.Companion.sort
import com.tonapps.tonkeeper.core.entities.StakedEntity
import com.tonapps.tonkeeper.extensions.hasPushPermission
import com.tonapps.tonkeeper.helper.DateHelper
import com.tonapps.tonkeeper.manager.tx.TransactionManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item.Status
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.NotificationEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.account.entities.WalletEvent
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.core.ScreenCacheSource
import com.tonapps.wallet.data.core.WalletCurrency
import com.tonapps.wallet.data.push.PushManager
import com.tonapps.wallet.data.push.entities.AppPushEntity
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.staking.entities.StakingEntity
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.data.token.entities.AccountTokenEntity
import com.tonapps.wallet.data.tonconnect.TonConnectRepository
import com.tonapps.wallet.data.tonconnect.entities.DConnectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.extensions.collectFlow
import uikit.extensions.context

class WalletViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val tokenRepository: TokenRepository,
    private val api: API,
    private val networkMonitor: NetworkMonitor,
    private val pushManager: PushManager,
    private val tonConnectRepository: TonConnectRepository,
    private val screenCacheSource: ScreenCacheSource,
    private val backupRepository: BackupRepository,
    private val stakingRepository: StakingRepository,
    private val ratesRepository: RatesRepository,
    private val batteryRepository: BatteryRepository,
    private val billingManager: BillingManager,
    private val transactionManager: TransactionManager
): BaseWalletVM(app) {

    private val alertNotificationsFlow = MutableStateFlow<List<NotificationEntity>>(emptyList())

    private val _uiLabelFlow = MutableStateFlow<Wallet.Label?>(null)
    val uiLabelFlow = _uiLabelFlow.asStateFlow()

    private val _lastLtFlow = MutableStateFlow(0L)
    private val _statusFlow = MutableStateFlow(Status.Updating)
    private val statusFlow = _statusFlow.asStateFlow()

    private val _stateMainFlow = MutableStateFlow<State.Main?>(null)
    private val stateMainFlow = _stateMainFlow.asStateFlow().filterNotNull()

    private val updateWalletSettings = combine(
        settingsRepository.tokenPrefsChangedFlow,
        settingsRepository.walletPrefsChangedFlow
    ) { _, _ -> }

    private val _stateSettingsFlow = combine(
        settingsRepository.hiddenBalancesFlow,
        api.configFlow,
        statusFlow,
    ) { hiddenBalance, config, status ->
        State.Settings(hiddenBalance, config, status)
    }.distinctUntilChanged()

    private val _uiItemsFlow = MutableStateFlow<List<Item>?>(null)
    val uiItemsFlow = _uiItemsFlow.asStateFlow().filterNotNull()

    val hasBackupFlow = backupRepository.stream.map { backups ->
        if (!wallet.hasPrivateKey) {
            true
        } else {
            backups.indexOfFirst { it.walletId == wallet.id } > -1
        }
    }.map { !it }

    private val _streamFLow = combine(updateWalletSettings, _lastLtFlow) { _, _ -> }

    init {
        /*collectFlow(accountRepository.realtimeEventsFlow) { event ->
            if (event is WalletEvent.Boc) {
                setStatus(Status.SendingTransaction)
            } else if (event is WalletEvent.Transaction) {

            }
        }*/

        collectFlow(transactionManager.eventsFlow(wallet)) { event ->
            if (event.inProgress) {
                setStatus(Status.SendingTransaction)
            } else {
                setStatus(Status.TransactionConfirmed)
                delay(2000)
                setStatus(Status.Default)
                _lastLtFlow.value = event.lt
            }
        }

        collectFlow(networkMonitor.isOnlineFlow) { online ->
            if (!online) {
                setStatus(Status.NoInternet)
                delay(3000)
                setStatus(Status.LastUpdated)
            }
        }

        combine(
            settingsRepository.currencyFlow,
            backupRepository.stream,
            networkMonitor.isOnlineFlow,
            _streamFLow,
        ) { currency, backups, isOnline, _ ->
            if (isOnline) {
                setStatus(Status.Updating)
            }
            _uiLabelFlow.value = wallet.label

            val hasBackup = if (!wallet.hasPrivateKey) {
                true
            } else {
                backups.indexOfFirst { it.walletId == wallet.id } > -1
            }

            val walletCurrency = getCurrency(wallet, currency)

            val localAssets = getLocalAssets(walletCurrency, wallet)
            val batteryBalance = getBatteryBalance(wallet)
            if (localAssets != null) {
                _stateMainFlow.value = State.Main(
                    wallet = wallet,
                    assets = localAssets,
                    hasBackup = hasBackup,
                    battery = State.Battery(
                        balance = batteryBalance,
                        beta = api.config.batteryBeta,
                        disabled = api.config.batteryDisabled,
                        viewed = settingsRepository.batteryViewed,
                    ),
                )
            }

            if (isOnline) {
                val remoteAssets = getRemoteAssets(walletCurrency, wallet)
                val batteryBalance = getBatteryBalance(wallet, true)
                if (remoteAssets != null) {
                    _stateMainFlow.value = State.Main(
                        wallet,
                        remoteAssets,
                        hasBackup = hasBackup,
                        battery = State.Battery(
                            balance = batteryBalance,
                            beta = api.config.batteryBeta,
                            disabled = api.config.batteryDisabled,
                            viewed = settingsRepository.batteryViewed,
                        ),
                    )
                    settingsRepository.setWalletLastUpdated(wallet.id)
                    setStatus(Status.Default)
                }
            }
        }.launchIn(viewModelScope)

        combine(
            stateMainFlow,
            alertNotificationsFlow,
            pushManager.dAppPushFlow,
            _stateSettingsFlow,
            updateWalletSettings,
        ) { state, alerts, dAppNotifications, settings, _ ->
            val status = settings.status /* if (settings.status == Status.NoInternet) {
                settings.status
            } else if (settings.status != Status.SendingTransaction && settings.status != Status.TransactionConfirmed) {
                state.status
            } else {
                settings.status
            }*/

            val dAppEvents = dAppNotifications ?: emptyList()
            val apps = getApps(state.wallet, dAppEvents)

            val isSetupHidden = settingsRepository.isSetupHidden(state.wallet.id)
            val uiSetup: State.Setup? = if (isSetupHidden) null else {
                val walletPushEnabled = settingsRepository.getPushWallet(state.wallet.id)
                State.Setup(
                    pushEnabled = context.hasPushPermission() && walletPushEnabled,
                    biometryEnabled = settingsRepository.biometric,
                    hasBackup = state.hasBackup,
                    showTelegramChannel = !settingsRepository.isTelegramChannel(state.wallet.id)
                )
            }

            val lastUpdated = settingsRepository.getWalletLastUpdated(state.wallet.id)

            val uiItems = state.uiItems(
                wallet = state.wallet,
                hiddenBalance = settings.hiddenBalance,
                status = status,
                config = settings.config,
                alerts = alerts,
                dAppNotifications = State.DAppNotifications(dAppEvents, apps),
                setup = uiSetup,
                lastUpdatedFormat = DateHelper.formattedDate(lastUpdated, settingsRepository.getLocale())
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
            val index = wallets.indexOf(wallet)
            val nextIndex = if (index == wallets.size - 1) 0 else index + 1
            accountRepository.setSelectedWallet(wallets[nextIndex].id)
        }
    }

    fun prevWallet() {
        viewModelScope.launch {
            val wallets = accountRepository.getWallets()
            val index = wallets.indexOf(wallet)
            val prevIndex = if (index == 0) wallets.size - 1 else index - 1
            accountRepository.setSelectedWallet(wallets[prevIndex].id)
        }
    }

    private fun setStatus(status: Status) {
        _statusFlow.tryEmit(status)
    }

    private suspend fun getBatteryBalance(
        wallet: WalletEntity,
        ignoreCache: Boolean = false
    ): Coins = withContext(Dispatchers.IO) {
        val tonProofToken = accountRepository.requestTonProofToken(wallet) ?: return@withContext Coins.ZERO
        val battery = batteryRepository.getBalance(
            tonProofToken = tonProofToken,
            publicKey = wallet.publicKey,
            testnet = wallet.testnet,
            ignoreCache = ignoreCache
        )
        return@withContext battery.balance
    }

    private suspend fun getLocalAssets(
        currency: WalletCurrency,
        wallet: WalletEntity
    ): State.Assets? = withContext(Dispatchers.IO) {
        val tokens = tokenRepository.getLocal(currency, wallet.accountId, wallet.testnet)
        if (tokens.isEmpty()) {
            return@withContext null
        }
        val initializedAccount = tokens.firstOrNull()?.balance?.initializedAccount ?: false
        val staking = stakingRepository.get(wallet.accountId, wallet.testnet, initializedAccount = initializedAccount)
        buildStateTokens(wallet, currency, tokens, staking, true)
    }

    private suspend fun getRemoteAssets(
        currency: WalletCurrency,
        wallet: WalletEntity
    ): State.Assets? = withContext(Dispatchers.IO) {
        try {
            val tokens = tokenRepository.getRemote(currency, wallet.accountId, wallet.testnet) ?: return@withContext null
            val initializedAccount = tokens.first().balance.initializedAccount
            val staking = stakingRepository.get(wallet.accountId, wallet.testnet, ignoreCache = true, initializedAccount = initializedAccount)
            buildStateTokens(wallet, currency, tokens, staking, false)
        } catch (e: Throwable) {
            return@withContext null
        }
    }

    private suspend fun buildStateTokens(
        wallet: WalletEntity,
        currency: WalletCurrency,
        tokens: List<AccountTokenEntity>,
        staking: StakingEntity,
        fromCache: Boolean
    ): State.Assets? {
        val fiatRates = ratesRepository.getTONRates(currency)
        val staked = StakedEntity.create(staking, tokens, currency, ratesRepository)
        val liquid = staked.find { it.isTonstakers }?.liquidToken

        val filteredTokens = if (liquid == null) {
            tokens
        } else {
            tokens.filter { !liquid.token.address.contains(it.address)  }
        }
        if (filteredTokens.isEmpty() && staked.isEmpty()) {
            return null
        }

        val assets = (filteredTokens.map { AssetsEntity.Token(it) } + staked.map {
            AssetsEntity.Staked(it)
        }).sortedBy { it.fiat }.reversed()

        return State.Assets(currency, assets.sort(wallet, settingsRepository), fromCache, fiatRates)
    }

    private fun getApps(
        wallet: WalletEntity,
        events: List<AppPushEntity>
    ): List<DConnectEntity> {
        if (true) { // events.isEmpty()
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