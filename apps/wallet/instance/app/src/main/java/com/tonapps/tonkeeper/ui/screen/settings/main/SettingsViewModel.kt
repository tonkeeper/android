package com.tonapps.tonkeeper.ui.screen.settings.main

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.model.legacy.WalletCurrency
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.blockchain.model.legacy.Wallet as TonWallet
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.blockchain.ton.contract.BaseWalletContract
import com.tonapps.blockchain.ton.contract.WalletVersion
import com.tonapps.blockchain.ton.extensions.toAccountId
import com.tonapps.bus.core.AnalyticsHelper
import com.tonapps.core.flags.WalletFeature
import com.tonapps.legacy.enteties.AssetsEntity
import com.tonapps.legacy.enteties.AssetsExtendedEntity
import com.tonapps.tonkeeper.Environment
import com.tonapps.tonkeeper.Wallet
import com.tonapps.tonkeeper.core.FirebaseHelper
import com.tonapps.tonkeeper.extensions.capitalized
import com.tonapps.tonkeeper.manager.push.PushManager
import com.tonapps.tonkeeper.manager.tonconnect.ITonConnectBridge
import com.tonapps.tonkeeper.manager.widget.WidgetManager
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.screen.settings.main.list.Item
import com.tonapps.tonkeeper.worker.PushToggleWorker
import com.tonapps.uikit.list.ListCell
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.ConfigEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.backup.entities.BackupEntity
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.core.SearchEngine
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.passcode.PasscodeManager
import com.tonapps.wallet.data.plugins.PluginsRepository
import com.tonapps.wallet.data.rn.RNLegacy
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.token.TokenRepository
import com.tonapps.wallet.localization.Language
import com.tonapps.wallet.localization.Localization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(
    application: Application,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val api: API,
    private val backupRepository: BackupRepository,
    private val tonConnectBridge: ITonConnectBridge,
    private val passcodeManager: PasscodeManager,
    private val rnLegacy: RNLegacy,
    private val environment: Environment,
    private val tokenRepository: TokenRepository,
    private val batteryRepository: BatteryRepository,
    private val pluginsRepository: PluginsRepository,
    private val analytics: AnalyticsHelper,
    private val mcAccountRepository: McAccountRepository,
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val pushManager: PushManager,
) : BaseWalletVM(application) {

    val walletFlow: StateFlow<Wallet?> = combine(
        accountRepository.selectedWalletFlow,
        settingsRepository.walletPrefsChangedFlow,
        mcAccountRepository.refreshTrigger,
    ) { selected, _, _ ->
        withContext(Dispatchers.IO) {
            resolveWallet(selected)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    private val settingsDataFlow: Flow<SettingsData> = combine(
        backupRepository.stream,
        settingsRepository.walletPrefsChangedFlow,
        settingsRepository.currencyFlow,
        settingsRepository.languageFlow,
        settingsRepository.searchEngineFlow,
    ) { backups, _, currency, language, searchEngine ->
        SettingsData(
            backups = backups,
            currency = currency,
            language = language,
            searchEngine = searchEngine,
        )
    }

    private val _uiItemsFlow = MutableStateFlow<List<Item>>(emptyList())
    val uiItemsFlow = _uiItemsFlow.asStateFlow().filter { it.isNotEmpty() }

    init {
        combine(
            walletFlow.filterNotNull(),
            settingsDataFlow,
            settingsRepository.walletPrefsChangedFlow,
        ) { wallet, settings, _ ->
            val kind = withContext(Dispatchers.IO) {
                refreshKind(wallet)
            }

            val hasBackup = settings.backups.any {
                it.walletId == kind.id
            }

            val hasNonMultichainWallets = withContext(Dispatchers.IO) {
                accountRepository.getWallets().any { candidate ->
                    candidate.hasPrivateKey &&
                        !candidate.testnet &&
                        !candidate.tetra &&
                        candidate.type != WalletType.Multichain
                }
            }

            buildUiItems(
                kind = kind,
                currency = settings.currency,
                language = settings.language,
                searchEngine = settings.searchEngine,
                hasBackup = hasBackup,
                hasNonMultichainWallets = hasNonMultichainWallets,
            )
        }.launchIn(viewModelScope)
    }

    val installId: String
        get() = settingsRepository.installId

    private val tokensFlow = settingsRepository.tokenPrefsChangedFlow.map {
        val legacy = (walletFlow.value as? Wallet.Legacy)?.entity ?: return@map emptyList()
        val safeMode = settingsRepository.isSafeModeEnabled(legacy.id, legacy.network)

        tokenRepository.mustGet(settingsRepository.currency, legacy.accountId, legacy.network)
            .mapNotNull { token ->
                if (safeMode && !token.verified) {
                    return@mapNotNull null
                }

                AssetsExtendedEntity(
                    raw = AssetsEntity.Token(token),
                    prefs = settingsRepository.getTokenPrefs(
                        legacy.id,
                        token.address,
                        token.blacklist
                    ),
                    accountId = legacy.accountId,
                )
            }
            .filter { !it.isTon }
            .sortedBy { it.index }
    }

    private suspend fun resolveWallet(selected: WalletEntity): Wallet? {
        val id = selected.id
        if (id.isBlank()) return null

        accountRepository.getWalletById(id)?.let {
            return Wallet.Legacy(it)
        }

        mcAccountRepository.getWallet(id)?.let {
            return Wallet.Multichain(it)
        }

        return null
    }

    private suspend fun refreshKind(wallet: Wallet): Wallet {
        return when (wallet) {
            is Wallet.Legacy -> wallet
            is Wallet.Multichain -> {
                mcAccountRepository.getWallet(wallet.id)?.let {
                    Wallet.Multichain(it)
                } ?: wallet
            }
        }
    }

    private fun mcToDisplayEntity(mc: McWalletEntity): WalletEntity {
        return WalletEntity.EMPTY.copy(
            id = mc.id,
            label = TonWallet.Label(mc.name, mc.emoji, mc.color),
            version = WalletVersion.V4R2,
            type = WalletType.Default,
        )
    }

    fun setSearchEngine(searchEngine: SearchEngine?) {
        val engine = searchEngine ?: SearchEngine.GOOGLE
        settingsRepository.searchEngine = engine
        FirebaseHelper.searchEngine(engine.title)
    }

    fun markMigrationOpened() {
        val walletId = (walletFlow.value as? Wallet.Multichain)?.id ?: return
        settingsRepository.setMigrationOpened(walletId)
    }

    fun signOut(callback: () -> Unit) {
        analytics.simpleTrackEvent("delete_wallet")

        when (val wallet = walletFlow.value) {
            is Wallet.Legacy -> {
                val entity = wallet.entity

                viewModelScope.launch(Dispatchers.IO) {
                    tonConnectBridge.clear(entity)
                    PushToggleWorker.run(context, entity, PushManager.State.Delete)
                    delay(2000)

                    withContext(Dispatchers.Main) {
                        callback()
                    }

                    unifiedAccountRepository.deleteWallet(entity.id)
                }
            }

            is Wallet.Multichain -> {
                viewModelScope.launch(Dispatchers.IO) {
                    val entity = unifiedAccountRepository.getTonWalletById(wallet.id)
                    entity?.let { tonConnectBridge.clear(it) }

                    // callback() before deleteWallet: re-selection can emit Empty and cancel this scope.
                    withContext(NonCancellable) {
                        withContext(Dispatchers.Main) {
                            callback()
                        }

                        unifiedAccountRepository.deleteWallet(wallet.id)

                        entity?.let {
                            pushManager.wallet(it, PushManager.State.Delete)
                        }
                    }
                }
            }

            null -> Unit
        }
    }

    fun createV4R2Wallet() {
        val wallet = (walletFlow.value as? Wallet.Legacy)?.entity ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val passcode = withContext(Dispatchers.Main) {
                passcodeManager.legacyGetPasscode(context)
            } ?: return@launch

            val newLabel = TonWallet.Label(
                accountName = wallet.label.accountName + " V4R2",
                emoji = wallet.label.emoji,
                color = wallet.label.color
            )

            val newWalletId = AccountRepository.newWalletId()
            val version = WalletVersion.V4R2
            val mnemonic = accountRepository.getMnemonic(wallet.id)?.toList() ?: return@launch
            val walletIds = listOf(newWalletId)
            val versions = listOf(version)

            rnLegacy.addMnemonics(passcode, walletIds, mnemonic)

            accountRepository.importWallet(
                walletIds,
                TonWallet.NewLabel(
                    names = listOf(newLabel.name),
                    emoji = newLabel.emoji,
                    color = newLabel.color,
                ),
                mnemonic,
                versions,
                wallet.type,
                listOf(false)
            )

            backupRepository.addBackup(newWalletId)
            accountRepository.setSelectedWallet(newWalletId)
            finish()
        }
    }

    private suspend fun computeHasW5(wallet: WalletEntity): Boolean {
        if (wallet.version == WalletVersion.V5R1) {
            return true
        }

        if (
            wallet.type == WalletType.Watch ||
            wallet.type == WalletType.Lockup ||
            wallet.type == WalletType.Ledger
        ) {
            return true
        }

        val w5Contact = BaseWalletContract.create(wallet.publicKey, "v5r1", wallet.network)
        val accountId = w5Contact.address.toAccountId()

        return accountRepository.getWalletByAccountId(accountId, wallet.network) != null
    }

    private suspend fun computeHasV4R2(wallet: WalletEntity): Boolean {
        if (wallet.version != WalletVersion.V5R1 && wallet.version != WalletVersion.V5BETA) {
            return true
        }

        if (
            wallet.type == WalletType.Watch ||
            wallet.type == WalletType.Lockup ||
            wallet.type == WalletType.Ledger
        ) {
            return true
        }

        val v4R2Contact = BaseWalletContract.create(wallet.publicKey, "v4r2", wallet.network)
        val accountId = v4R2Contact.address.toAccountId()

        return accountRepository.getWalletByAccountId(accountId, wallet.network) != null
    }

    private suspend fun buildUiItems(
        kind: Wallet,
        currency: WalletCurrency,
        language: Language,
        searchEngine: SearchEngine,
        hasBackup: Boolean,
        hasNonMultichainWallets: Boolean,
    ) {
        _uiItemsFlow.value = when (kind) {
            is Wallet.Legacy -> buildLegacyUiItems(
                wallet = kind.entity,
                currency = currency,
                language = language,
                searchEngine = searchEngine,
                hasBackup = hasBackup,
            )

            is Wallet.Multichain -> buildMultichainUiItems(
                mc = kind.entity,
                currency = currency,
                language = language,
                searchEngine = searchEngine,
                hasBackup = hasBackup,
                hasNonMultichainWallets = hasNonMultichainWallets,
            )
        }
    }

    private suspend fun buildLegacyUiItems(
        wallet: WalletEntity,
        currency: WalletCurrency,
        language: Language,
        searchEngine: SearchEngine,
        hasBackup: Boolean,
    ): List<Item> {
        val config = api.getConfig(wallet.network)
        val hasW5Wallet = computeHasW5(wallet)
        val hasV4R2Wallet = computeHasV4R2(wallet)

        val uiItems = mutableListOf<Item>()
        uiItems.add(Item.Account(wallet))

        uiItems.add(Item.Space)

        if (wallet.hasPrivateKey) {
            uiItems.add(Item.Backup(ListCell.Position.FIRST, hasBackup))
            uiItems.add(Item.Security(ListCell.Position.LAST))
        } else {
            uiItems.add(Item.Security(ListCell.Position.SINGLE))
        }

        uiItems.add(Item.Space)

        if (environment.isGooglePlayServicesAvailable) {
            uiItems.add(Item.Notifications(ListCell.Position.FIRST))
        }

        var secondCellPosition = if (environment.isGooglePlayServicesAvailable) {
            ListCell.Position.MIDDLE
        } else {
            ListCell.Position.FIRST
        }

        if (wallet.hasPrivateKey) {
            if (!hasW5Wallet) {
                uiItems.add(Item.W5(secondCellPosition))
                secondCellPosition = ListCell.Position.MIDDLE
            }

            if (!hasV4R2Wallet) {
                uiItems.add(Item.V4R2(secondCellPosition))
                secondCellPosition = ListCell.Position.MIDDLE
            }
        }

        if (!wallet.testnet) {
            uiItems.add(Item.Currency(currency.code, secondCellPosition))
            secondCellPosition = ListCell.Position.MIDDLE
        }

        if (wallet.isTonConnectSupported) {
            uiItems.add(Item.SearchEngine(searchEngine, secondCellPosition))
            uiItems.add(Item.ConnectedApps(ListCell.Position.MIDDLE))

            if (hasInstalledExtensions(wallet) && (wallet.hasPrivateKey || wallet.signer)) {
                uiItems.add(Item.InstalledExtensions(ListCell.Position.MIDDLE))
            }
        }

        uiItems.add(
            Item.Language(
                language.nameLocalized.ifEmpty {
                    getString(Localization.system)
                }.capitalized,
                ListCell.Position.MIDDLE
            )
        )

        if (
            wallet.hasPrivateKey &&
            (!config.flags.disableBattery || getBatteryCharges(wallet) > 0)
        ) {
            uiItems.add(Item.Battery(ListCell.Position.MIDDLE))
        }

        if (WidgetManager.isRequestPinAppWidgetSupported) {
            uiItems.add(Item.Widget(ListCell.Position.MIDDLE))
        }

        uiItems.add(Item.Theme(ListCell.Position.LAST))

        uiItems.addSettingsFooter(config)

        uiItems.add(Item.Space)

        if (wallet.type == WalletType.Watch) {
            uiItems.add(Item.DeleteWatchAccount(ListCell.Position.SINGLE))
        } else {
            uiItems.add(
                Item.Logout(
                    ListCell.Position.SINGLE,
                    wallet.label,
                    !wallet.hasPrivateKey
                )
            )
        }

        uiItems.add(Item.Space)
        uiItems.add(Item.Logo(environment.installerSource))

        return uiItems
    }

    private suspend fun buildMultichainUiItems(
        mc: McWalletEntity,
        currency: WalletCurrency,
        language: Language,
        searchEngine: SearchEngine,
        hasBackup: Boolean,
        hasNonMultichainWallets: Boolean,
    ): List<Item> {
        val displayWallet = mcToDisplayEntity(mc)
        val config = api.getConfig(TonNetwork.MAINNET)

        val uiItems = mutableListOf<Item>()
        uiItems.add(Item.Account(displayWallet))
        uiItems.add(Item.Space)

        if (hasNonMultichainWallets && WalletFeature.Migration.isEnabled) {
            uiItems.add(
                Item.Migration(
                    position = ListCell.Position.SINGLE,
                    showDot = !settingsRepository.isMigrationOpened(mc.id),
                )
            )
            uiItems.add(Item.Space)
        }

        uiItems.add(Item.Backup(ListCell.Position.FIRST, hasBackup))
        var cellPosition = ListCell.Position.MIDDLE

        if (environment.isGooglePlayServicesAvailable) {
            uiItems.add(Item.Notifications(cellPosition))
            cellPosition = ListCell.Position.MIDDLE
        }

        uiItems.add(Item.Currency(currency.code, cellPosition))
        uiItems.add(Item.ConnectedApps(ListCell.Position.LAST))

        uiItems.add(Item.Space)

        uiItems.add(Item.Security(ListCell.Position.FIRST))
        uiItems.add(Item.Theme(ListCell.Position.MIDDLE))
        uiItems.add(Item.SearchEngine(searchEngine, ListCell.Position.MIDDLE))
        uiItems.add(
            Item.Language(
                language.nameLocalized.ifEmpty {
                    getString(Localization.system)
                }.capitalized,
                ListCell.Position.LAST,
            )
        )

        uiItems.addSettingsFooter(config)

        uiItems.add(Item.Space)
        uiItems.add(
            Item.Logout(
                ListCell.Position.SINGLE,
                displayWallet.label,
                delete = false
            )
        )
        uiItems.add(Item.Space)
        uiItems.add(Item.Logo(environment.installerSource))

        return uiItems
    }

    private fun MutableList<Item>.addSettingsFooter(config: ConfigEntity) {
        add(Item.Space)
        add(Item.FAQ(ListCell.Position.FIRST, config.faqUrl))
        add(Item.Support(ListCell.Position.MIDDLE))
        add(Item.News(ListCell.Position.MIDDLE, config.tonkeeperNewsUrl))

        if (environment.isGooglePlayServicesAvailable) {
            add(Item.Rate(ListCell.Position.MIDDLE))
        }

        add(Item.Legal(ListCell.Position.LAST))
    }

    private suspend fun getBatteryCharges(wallet: WalletEntity): Int = withContext(Dispatchers.IO) {
        batteryRepository.getCharges(wallet, true)
    }

    private suspend fun hasInstalledExtensions(wallet: WalletEntity): Boolean = withContext(Dispatchers.IO) {
        val plugins = pluginsRepository.getPlugins(wallet.accountId, wallet.network)
        plugins.isNotEmpty()
    }
}

private data class SettingsData(
    val backups: List<BackupEntity>,
    val currency: WalletCurrency,
    val language: Language,
    val searchEngine: SearchEngine,
)
