package com.tonapps.tonkeeper.ui.screen.wallet.main

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.icu.Coins
import com.tonapps.network.NetworkMonitor
import com.tonapps.tonkeeper.core.entities.AssetsEntity.Companion.sort
import com.tonapps.tonkeeper.extensions.hasPushPermission
import com.tonapps.tonkeeper.helper.DateHelper
import com.tonapps.tonkeeper.manager.apk.APKManager
import com.tonapps.tonkeeper.manager.assets.AssetsManager
import com.tonapps.tonkeeper.manager.tx.TransactionManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item
import com.tonapps.tonkeeper.ui.screen.wallet.main.list.Item.Status
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.NotificationEntity
import com.tonapps.wallet.data.account.entities.WalletEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.account.Wallet
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.core.ScreenCacheSource
import com.tonapps.wallet.data.core.currency.WalletCurrency
import com.tonapps.wallet.data.rates.RatesRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uikit.extensions.collectFlow
import kotlin.time.Duration.Companion.minutes

class WalletViewModel(
    app: Application,
    private val wallet: WalletEntity,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val api: API,
    private val networkMonitor: NetworkMonitor,
    private val screenCacheSource: ScreenCacheSource,
    private val backupRepository: BackupRepository,
    private val ratesRepository: RatesRepository,
    private val batteryRepository: BatteryRepository,
    private val transactionManager: TransactionManager,
    private val assetsManager: AssetsManager,
    private val apkManager: APKManager,
) : BaseWalletVM(app) {

    val installId: String
        get() = settingsRepository.installId

    private var autoRefreshJob: Job? = null
    private val alertNotificationsFlow = MutableStateFlow<List<NotificationEntity>>(emptyList())

    private val _uiLabelFlow = MutableStateFlow<Wallet.Label?>(null)
    val uiLabelFlow = _uiLabelFlow.asStateFlow()

    private val _lastLtFlow = MutableStateFlow(0L)
    private val _statusFlow = MutableStateFlow<Status?>(null)
    val statusFlow = _statusFlow.asStateFlow().filterNotNull()

    private val _stateMainFlow = MutableStateFlow<State.Main?>(null)
    private val stateMainFlow = _stateMainFlow.asStateFlow().filterNotNull()

    private val updateWalletSettings = combine(
        settingsRepository.tokenPrefsChangedFlow,
        settingsRepository.walletPrefsChangedFlow,
        settingsRepository.safeModeStateFlow,
    ) { _, _, _ -> }

    private val _stateSettingsFlow = combine(
        settingsRepository.hiddenBalancesFlow,
        statusFlow,
    ) { hiddenBalance, status ->
        State.Settings(hiddenBalance, api.config, status)
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

    private val _streamFlow = combine(
        updateWalletSettings,
        batteryRepository.balanceUpdatedFlow,
        transactionManager.tronUpdatedFlow,
        _lastLtFlow
    ) { _, _, _, lastLt -> lastLt }

    init {
        viewModelScope.launch {
            val cached = screenCacheSource.getWalletScreen(wallet) ?: listOf(Item.Skeleton(true))
            _uiItemsFlow.value = cached
        }

        collectFlow(transactionManager.eventsFlow(wallet)) { event ->
            if (event.pending) {
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
            _streamFlow,
            apkManager.statusFlow,
        ) { currency, backups, currentIsOnline, currentLt, apkStatus ->
            val lastLt = _stateMainFlow.value?.lt ?: 0
            val lastIsOnline = _stateMainFlow.value?.isOnline

            val isRequestUpdate =
                _stateMainFlow.value == null || lastLt != currentLt || lastIsOnline != currentIsOnline

            if (isRequestUpdate) {
                setStatus(Status.Updating)
            }

            _uiLabelFlow.value = wallet.label

            val hasBackup = if (!wallet.hasPrivateKey) {
                true
            } else {
                backups.indexOfFirst { it.walletId == wallet.id } > -1
            }

            val walletCurrency = getCurrency(wallet, currency)

            val localAssets = getAssets(walletCurrency, false)
            if (localAssets != null) {
                val batteryBalance = getBatteryBalance(wallet)
                val state = State.Main(
                    wallet = wallet,
                    assets = localAssets,
                    hasBackup = hasBackup,
                    battery = State.Battery(
                        balance = batteryBalance,
                        beta = api.config.batteryBeta,
                        disabled = api.config.batteryDisabled,
                        viewed = settingsRepository.batteryViewed,
                    ),
                    lt = currentLt,
                    isOnline = currentIsOnline,
                    apkStatus = apkStatus,
                    tronUsdtEnabled = settingsRepository.getTronUsdtEnabled(wallet.id)
                )
                assetsManager.setCachedTotalBalance(
                    wallet,
                    walletCurrency,
                    true,
                    state.totalBalanceFiat
                )
                _stateMainFlow.value = state
            }

            if (isRequestUpdate) {
                val remoteAssets = getAssets(walletCurrency, true)
                val batteryBalance = getBatteryBalance(wallet, true)
                if (remoteAssets != null) {
                    val state = State.Main(
                        wallet,
                        remoteAssets,
                        hasBackup = hasBackup,
                        battery = State.Battery(
                            balance = batteryBalance,
                            beta = api.config.batteryBeta,
                            disabled = api.config.batteryDisabled,
                            viewed = settingsRepository.batteryViewed,
                        ),
                        lt = currentLt,
                        isOnline = currentIsOnline,
                        apkStatus = apkStatus,
                        tronUsdtEnabled = settingsRepository.getTronUsdtEnabled(wallet.id)
                    )
                    _stateMainFlow.value = state
                    assetsManager.setCachedTotalBalance(
                        wallet,
                        walletCurrency,
                        true,
                        state.totalBalanceFiat
                    )
                    settingsRepository.setWalletLastUpdated(wallet.id)
                    setStatus(Status.Default)
                }
            }
        }.launchIn(viewModelScope)

        combine(
            stateMainFlow,
            alertNotificationsFlow,
            _stateSettingsFlow,
            updateWalletSettings,
        ) { state, alerts, settings, _ ->
            val status = settings.status /* if (settings.status == Status.NoInternet) {
                settings.status
            } else if (settings.status != Status.SendingTransaction && settings.status != Status.TransactionConfirmed) {
                state.status
            } else {
                settings.status
            }*/

            val isSetupHidden = settingsRepository.isSetupHidden(state.wallet.id)
            val uiSetup: State.Setup? = if (isSetupHidden) null else {
                val walletPushEnabled = settingsRepository.getPushWallet(state.wallet.id)
                val hasInitializedWallet = accountRepository.getInitializedWallets().isNotEmpty()
                State.Setup(
                    pushEnabled = context.hasPushPermission() && walletPushEnabled,
                    biometryEnabled = if (wallet.hasPrivateKey) settingsRepository.biometric else true,
                    hasBackup = if (wallet.hasPrivateKey) state.hasBackup else true,
                    showTelegramChannel = !settingsRepository.isTelegramChannel(state.wallet.id),
                    safeModeBlock = !api.config.flags.safeModeEnabled && hasInitializedWallet && settingsRepository.showSafeModeSetup,
                )
            }

            val lastUpdated = settingsRepository.getWalletLastUpdated(state.wallet.id)

            val uiItems = state.uiItems(
                context = context,
                wallet = state.wallet,
                hiddenBalance = settings.hiddenBalance,
                status = status,
                config = settings.config,
                alerts = alerts,
                dAppNotifications = State.DAppNotifications(emptyList()),
                setup = uiSetup,
                lastUpdatedFormat = DateHelper.formattedDate(
                    lastUpdated,
                    settingsRepository.getLocale()
                ),
                prefixYourAddress = 3 > settingsRepository.addressCopyCount
            )
            if (uiItems.isNotEmpty()) {
                _uiItemsFlow.value = uiItems
                setCached(state.wallet, uiItems)
            }
        }.launchIn(viewModelScope)

        loadAlertNotifications()

        autoRefreshJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                checkAutoRefresh()
                delay(2.minutes)
            }
        }
    }

    fun refresh() {
        _statusFlow.value = Status.Updating
        _lastLtFlow.value += 1
    }

    private suspend fun checkAutoRefresh() {
        if (hasPendingTransaction()) {
            withContext(Dispatchers.Main) {
                refresh()
            }
        }
    }

    private fun hasPendingTransaction(): Boolean {
        return _statusFlow.value == Status.SendingTransaction
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
        if (wallet.hasPrivateKey) {
            val tonProofToken =
                accountRepository.requestTonProofToken(wallet) ?: return@withContext Coins.ZERO
            val battery = batteryRepository.getBalance(
                tonProofToken = tonProofToken,
                publicKey = wallet.publicKey,
                testnet = wallet.testnet,
                ignoreCache = ignoreCache
            )
            battery.balance
        } else {
            Coins.ZERO
        }
    }

    private suspend fun getAssets(
        currency: WalletCurrency,
        refresh: Boolean
    ): State.Assets? = withContext(Dispatchers.IO) {
        assetsManager.getAssets(wallet, currency, refresh)?.let {
            State.Assets(
                currency = currency,
                list = it.sort(wallet, settingsRepository),
                fromCache = !refresh,
                rates = ratesRepository.getTONRates(currency)
            )
        }
    }

    private fun setCached(wallet: WalletEntity, items: List<Item>) {
        screenCacheSource.set(CACHE_NAME, wallet.id, items)
    }

    override fun onCleared() {
        super.onCleared()
        autoRefreshJob?.cancel()
        autoRefreshJob = null
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
                }.filter { it !is Item.ApkStatus }
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