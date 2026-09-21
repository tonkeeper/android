package com.tonapps.portfolio.screens.wallet

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.filter
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.ton.TonNetwork
import com.tonapps.chainkit.core.chain.model.num.CoinValue
import com.tonapps.chainkit.core.chain.model.num.Decimal
import com.tonapps.chainkit.core.chain.model.num.DisplayUnit
import com.tonapps.chainkit.core.chain.model.num.FiatCurrency
import com.tonapps.core.deeplink.isRaffleDeeplink
import com.tonapps.core.flags.WalletFeature.Migration
import com.tonapps.core.flags.WalletFeature.Raffles
import com.tonapps.extensions.fiatSymbol
import com.tonapps.log.L
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.mvi.MviRelay
import com.tonapps.mvi.flow.flatMapLatestCatching
import com.tonapps.network.NetworkMonitor
import com.tonapps.core.paging.AccountsPagingCacheSession
import com.tonapps.core.paging.AccountsPagingSource
import com.tonapps.wallet.api.API
import com.tonapps.wallet.api.entity.BannerEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.backup.BackupRepository
import com.tonapps.wallet.data.backup.entities.BackupEntity
import com.tonapps.wallet.data.banner.BannerRepository
import com.tonapps.wallet.data.battery.BatteryRepository
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.collectibles.entities.NftEntity
import com.tonapps.wallet.data.core.Trust
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.multichain.account.WALLET_ASSETS_PAGE_SIZE
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import com.tonapps.wallet.data.raffle.RaffleClock
import com.tonapps.wallet.data.raffle.RaffleRepository
import com.tonapps.wallet.data.raffle.entities.RaffleEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.settings.entities.TokenPrefsEntity
import com.tonapps.wallet.data.staking.StakingRepository
import com.tonapps.wallet.data.tx.TransactionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration

data class WalletTotal(
    val walletId: String,
    val currencyCode: String,
    val currency: FiatCurrency,
    val value: CoinValue,
)

data class WalletBattery(
    val level: Float,
    val viewed: Boolean,
    val negative: Boolean,
)

enum class WalletAction {
    Send, Deposit, Swap, Stake
}

class WalletFeature(
    val accountRepo: McAccountRepository,
    val accountRepoLegacy: AccountRepository,
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val settingsRepository: SettingsRepository,
    private val batteryRepository: BatteryRepository,
    private val backupRepository: BackupRepository,
    private val bannerRepository: BannerRepository,
    private val raffleRepository: RaffleRepository,
    private val collectiblesRepository: CollectiblesRepository,
    private val stakingRepository: StakingRepository,
    private val networkMonitor: NetworkMonitor,
    private val transactionManager: TransactionManager,
    private val api: API,
    private val pushStatusProvider: PushStatusProvider,
    private val migrationStatusProvider: MigrationStatusProvider,
    private val biometryStatusProvider: BiometryStatusProvider,
) : AsyncViewModel() {

    private val refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val accountsPagingCacheSession = AccountsPagingCacheSession()

    private val errorRelay = MviRelay<Throwable>()
    val error: Flow<Throwable> = errorRelay.events

    val wallet = combine(
        accountRepoLegacy.selectedWalletFlow,
        settingsRepository.walletPrefsChangedFlow,
        accountRepo.refreshTrigger,
    ) { selected, _, _ ->
        accountRepo.getWallet(selected.id)
    }.cacheState()

    val settingsBadge = combine(wallet, backupRepository.stream) { mcWallet, backups ->
        mcWallet != null && backups.none { it.walletId == mcWallet.id }
    }.cacheState(initialValue = false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val tonAddress: StateFlow<String?> = wallet
        .mapNotNull { it?.id }
        .distinctUntilChanged()
        .mapLatest { walletId -> loadTonAddress(walletId) }
        .cacheState()

    val pushEnabling: StateFlow<Boolean> field = MutableStateFlow(false)

    private val assetsTotal = MutableStateFlow<WalletTotal?>(null)

    private val selectedWalletId = accountRepoLegacy.selectedWalletFlow.map { it.id }

    // Finalized on-chain events of the selected wallet (SSE); each one changes balances and may
    // have granted raffle tickets server-side, so it refreshes both the assets and the raffles.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val onChainEvents = accountRepoLegacy.selectedWalletFlow
        .flatMapLatest { transactionManager.eventsFlow(it) }
        .filter { !it.pending }

    private val walletIdRefresh = merge(
        selectedWalletId,
        refreshSignal.mapNotNull { accountRepoLegacy.getSelectedWalletId() },
        onChainEvents.mapNotNull { accountRepoLegacy.getSelectedWalletId() },
    )

    private val setupRefresh = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val raffles: StateFlow<List<RaffleEntity>> = merge(
        selectedWalletId.map { it to false },
        refreshSignal.mapNotNull { accountRepoLegacy.getSelectedWalletId()?.let { it to true } },
        onChainEvents.mapNotNull { accountRepoLegacy.getSelectedWalletId() }.map { it to true },
    )
        .flatMapLatestCatching { (walletId, forced) ->
            raffleRepository.getRafflesFlow(walletId, forced)
        }
        .cacheState(initialValue = emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val raffleRows: StateFlow<List<RaffleEntity>> = raffles
        .transformLatest { raffles ->
            while (true) {
                val now = RaffleClock.now()
                emit(raffles.filter { it.hasStarted(now) && !it.isOfferPhase })
                val nextBoundary = raffles
                    .map { it.startsAt }
                    .filter { it.isAfter(now) }
                    .minOrNull()
                    ?: break
                delay(Duration.between(RaffleClock.now(), nextBoundary).toMillis().coerceAtLeast(0))
            }
        }
        .cacheState(initialValue = emptyList())

    // The wallet screen gets its raffle banner through the shared banners feed
    // (backend inserts it there); the raffle endpoint's own banner feeds other
    // surfaces, e.g. the trading screen.
    @OptIn(ExperimentalCoroutinesApi::class)
    val banners: StateFlow<List<BannerEntity>> = walletIdRefresh
        .flatMapLatest { walletId ->
            bannerRepository.getBannersFlow(walletId, isMultichain = true).map { backend ->
                if (Raffles.isEnabled) {
                    backend
                } else {
                    backend.filterNot { it.button?.payload?.let(::isRaffleDeeplink) == true }
                }
            }
        }
        .cacheState(initialValue = emptyList())

    val actions = listOf(
        WalletAction.Send,
        WalletAction.Deposit,
        WalletAction.Swap,
        WalletAction.Stake,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val accountsFlow = combine(
        walletIdRefresh,
        settingsRepository.currencyFlow,
        settingsRepository.safeModeChangedFlow,
        settingsRepository.hideDustAssetsFlow,
    ) { walletId, walletCurrency, _, _ ->
        walletId to walletCurrency.code
    }
        .flatMapLatest { (walletId, currencyCode) ->
            val previous = assetsTotal.value
            if (previous != null &&
                (previous.walletId != walletId || previous.currencyCode != currencyCode)
            ) {
                assetsTotal.value = null
            }
            val safeMode = settingsRepository.isSafeModeEnabled(walletId, TonNetwork.MAINNET)
            val hideDust = settingsRepository.hideDustAssets
            Pager(
                config = PagingConfig(
                    initialLoadSize = WALLET_ASSETS_PAGE_SIZE,
                    pageSize = WALLET_ASSETS_PAGE_SIZE,
                    prefetchDistance = 5,
                    enablePlaceholders = false,
                ),
                pagingSourceFactory = {
                    AccountsPagingSource(
                        accountRepo = accountRepo,
                        walletId = walletId,
                        currency = currencyCode,
                        verifiedOnly = safeMode,
                        hideDust = hideDust,
                        cacheSession = accountsPagingCacheSession,
                        scope = viewModelScope,
                        onTotalLoaded = { result ->
                            assetsTotal.tryEmit(
                                WalletTotal(
                                    walletId = walletId,
                                    currencyCode = currencyCode,
                                    currency = FiatCurrency(currencyCode.fiatSymbol()),
                                    value = DisplayUnit(result.total, Decimal(2)),
                                )
                            )
                        },
                        onLoadError = { error -> handleRefreshError(error) },
                    )
                },
            ).flow.map { pagingData ->
                if (safeMode) {
                    pagingData.filter { it.asset.verification.isVerified }
                } else {
                    pagingData
                }
            }
        }
        .cachedIn(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val collectibles: StateFlow<WalletCollectiblesState?> = combine(
        walletIdRefresh,
        settingsRepository.tokenPrefsChangedFlow,
        settingsRepository.safeModeChangedFlow,
        networkMonitor.isOnlineFlow,
    ) { walletId, _, _, isOnline ->
        collectiblesFlow(walletId, isOnline)
    }.flatMapLatest { it }.cacheState(initialValue = null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val stakingApy: StateFlow<String?> = walletIdRefresh
        .mapLatest { walletId -> loadMaxStakingApy(walletId) }
        .cacheState()

    @OptIn(ExperimentalCoroutinesApi::class)
    val battery: StateFlow<WalletBattery?> = merge(
        selectedWalletId.map { it to false },
        refreshSignal.mapNotNull { accountRepoLegacy.getSelectedWalletId()?.let { it to true } },
        batteryRepository.balanceUpdatedFlow.mapNotNull { wallet.value?.id?.let { it to false } },
    )
        .mapLatest { (walletId, ignoreCache) -> loadBattery(walletId, ignoreCache) }
        .cacheState()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val stakedLoad: StateFlow<StakedLoad> = combine(
        merge(
            selectedWalletId.map { it to false },
            refreshSignal.mapNotNull { accountRepoLegacy.getSelectedWalletId()?.let { it to true } },
        ),
        settingsRepository.currencyFlow,
        settingsRepository.hiddenBalancesFlow,
    ) { (walletId, ignoreCache), currency, _ ->
        Triple(walletId, currency.code, ignoreCache)
    }
        .transformLatest { (walletId, currencyCode, ignoreCache) ->
            emit(
                StakedLoad(
                    walletId = walletId,
                    currencyCode = currencyCode,
                    items = loadStaked(walletId, ignoreCache),
                )
            )
        }
        .cacheState(initialValue = StakedLoad.Empty)

    val staked: StateFlow<List<StakedUi>> = combine(
        stakedLoad,
        selectedWalletId,
        settingsRepository.currencyFlow,
    ) { load, walletId, currency ->
        if (load.walletId != walletId || load.currencyCode != currency.code) {
            emptyList()
        } else {
            load.items
        }
    }.cacheState(initialValue = emptyList())

    val total: StateFlow<WalletTotal?> = combine(assetsTotal, stakedLoad) { assets, stakes ->
        if (assets == null) {
            return@combine null
        }
        if (stakes.walletId != assets.walletId || stakes.currencyCode != assets.currencyCode) {
            return@combine assets
        }
        assets.plusNominatorStakes(stakes.items)
    }.cacheState()

    // The count needs network, so it lives outside the main combine: the card renders right away
    // and the Migration row appears once the count arrives.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val migratableWallets: StateFlow<Int> = combine(
        wallet.filterNotNull(),
        setupRefresh,
    ) { mc, _ -> mc }
        .mapLatest { migratableWalletsCount() }
        .cacheState(initialValue = 0)

    val finishSetup = combine(
        wallet.filterNotNull(),
        backupRepository.stream,
        settingsRepository.walletPrefsChangedFlow,
        setupRefresh,
        total,
    ) { mc, backups, _, _, total ->
        Triple(mc, backups, total)
    }.combine(migratableWallets) { (mc, backups, total), migratableWallets ->
        buildFinishSetupState(mc, backups, total, migratableWallets)
    }.cacheState()

    init {
        bgScope.launch {
            accountRepo.refreshTrigger.collect {
                refreshSignal.tryEmit(Unit)
            }
        }
        bgScope.launch {
            settingsRepository.walletPush.collect {
                pushEnabling.value = false
                setupRefresh.update { it + 1 }
            }
        }
        bgScope.launch {
            settingsRepository.biometricFlow.collect {
                setupRefresh.update { it + 1 }
            }
        }
    }

    fun onEnablePushStarted() {
        pushEnabling.value = true
    }

    fun refreshPushStatus() {
        pushEnabling.value = false
        setupRefresh.update { it + 1 }
    }

    private fun buildFinishSetupState(
        mc: McWalletEntity,
        backups: List<BackupEntity>,
        total: WalletTotal?,
        migratableWallets: Int,
    ): FinishSetupState? {
        val hasBackup = backups.any { it.walletId == mc.id }
        val funded = total != null && total.walletId == mc.id &&
            total.value.toDisplayValue() > BigDecimal.ZERO
        if (funded && !hasBackup) {
            return FinishSetupState(
                lines = listOf(FinishSetupLine.Backup(status = FinishSetupLineStatus.Pending)),
                skippable = false,
            )
        }
        if (settingsRepository.isSetupHidden(mc.id)) {
            return null
        }
        val needsPushSetup = pushStatusProvider.needsPushSetup()
        val lines = buildList {
            if (!hasBackup) {
                add(FinishSetupLine.Backup(status = FinishSetupLineStatus.Pending))
            }
            if (migratableWallets > 0) {
                add(
                    FinishSetupLine.Migration(
                        status = FinishSetupLineStatus.Pending,
                        walletsLeft = migratableWallets,
                    ),
                )
            }
            if (needsPushSetup) {
                add(FinishSetupLine.Push(status = FinishSetupLineStatus.Pending))
            }
            if (!settingsRepository.biometric && biometryStatusProvider.isBiometryAvailable()) {
                add(FinishSetupLine.Biometry(status = FinishSetupLineStatus.Pending))
            }
        }
        if (lines.isEmpty()) {
            return null
        }
        return FinishSetupState(lines = lines, skippable = true)
    }

    private suspend fun migratableWalletsCount(): Int {
        if (!Migration.isEnabled) {
            return 0
        }
        return try {
            migrationStatusProvider.migratableWalletsCount()
        } catch (e: Throwable) {
            verifyError(e)
            L.e(e, "Failed to count migratable wallets")
            0
        }
    }

    fun skipFinishSetup() {
        val id = wallet.value?.id ?: return
        settingsRepository.setupHide(id)
    }

    fun hideBanner(banner: BannerEntity) {
        val walletId = wallet.value?.id ?: return
        bgScope.launch {
            bannerRepository.hideBanner(walletId, banner.id)
        }
    }

    fun refreshAssets() {
        refreshSignal.tryEmit(Unit)
        setupRefresh.update { it + 1 }
    }

    private fun handleRefreshError(error: Throwable) {
        errorRelay.emit(error)
    }

    private suspend fun loadMaxStakingApy(walletId: String): String? {
        val tonWallet = withContext(Dispatchers.IO) {
            unifiedAccountRepository.getTonWalletById(walletId)
        } ?: return null

        if (tonWallet.testnet) {
            return null
        }

        return stakingRepository.getMaxApyFormatted(
            accountId = tonWallet.accountId,
            network = tonWallet.network,
            initializedAccount = tonWallet.initialized,
        )
    }

    private suspend fun loadBattery(
        walletId: String,
        ignoreCache: Boolean = false,
    ): WalletBattery? = withContext(Dispatchers.IO) {
        val tonWallet = unifiedAccountRepository.getTonWalletById(walletId) ?: return@withContext null
        if (tonWallet.testnet) {
            return@withContext null
        }

        val balance = batteryRepository.getBalance(tonWallet, ignoreCache).balance

        if (api.getConfig(tonWallet.network).flags.disableBattery && !balance.isPositive) {
            return@withContext null
        }

        WalletBattery(
            level = balance.value.toFloat(),
            viewed = settingsRepository.batteryViewed,
            negative = balance.isNegative,
        )
    }

    private suspend fun loadTonAddress(walletId: String): String? {
        return try {
            accountRepo.getTonAccount(walletId)?.displayAddress
        } catch (e: Throwable) {
            verifyError(e)
            L.e(e, "Failed to load TON address")
            null
        }
    }

    private suspend fun loadStaked(
        walletId: String,
        ignoreCache: Boolean = false,
    ): List<StakedUi> = withContext(Dispatchers.IO) {
        val tonWallet = unifiedAccountRepository.getTonWalletById(walletId) ?: return@withContext emptyList()
        if (tonWallet.testnet) {
            return@withContext emptyList()
        }
        StakingAssetsBridge.loadRows(
            walletId = walletId,
            accountId = tonWallet.accountId,
            network = tonWallet.network,
            initializedAccount = tonWallet.initialized,
            currencyCode = settingsRepository.currency.code,
            hiddenBalance = settingsRepository.hiddenBalances,
            hideDust = settingsRepository.hideDustAssets,
            accountRepo = accountRepo,
            stakingRepository = stakingRepository,
            ignoreCache = ignoreCache,
        )
    }

    private fun collectiblesFlow(
        walletId: String,
        isOnline: Boolean,
    ): Flow<WalletCollectiblesState?> = flow {
        emit(null)
        val tonWallet = withContext(Dispatchers.IO) {
            unifiedAccountRepository.getTonWalletById(walletId)
        } ?: return@flow
        if (api.getConfig(tonWallet.network).flags.disableNfts) {
            return@flow
        }
        emitAll(
            flow {
                collectiblesRepository.getFlow(tonWallet.address, tonWallet.network, isOnline).collect { result ->
                    emit(mapCollectiblesState(wallet = tonWallet, result = result))
                }
            },
        )
    }.flowOn(Dispatchers.IO)

    private suspend fun mapCollectiblesState(
        wallet: WalletEntity,
        result: com.tonapps.wallet.data.collectibles.entities.NftListResult,
    ): WalletCollectiblesState? {
        if (result.list.isEmpty()) {
            return null
        }
        val safeMode = settingsRepository.isSafeModeEnabled(wallet.id, wallet.network)
        var hasUserHidden = false
        val items = buildList {
            for (nft in result.list) {
                if (safeMode && !nft.verified) {
                    continue
                }
                val nftPref = settingsRepository.getTokenPrefs(wallet.id, nft.collectionAddressOrNFTAddress)
                if (nftPref.isHidden) {
                    hasUserHidden = true
                    continue
                }
                add(applyNftPref(nft, nftPref))
            }
        }
        return when {
            items.isNotEmpty() -> WalletCollectiblesState(items = items)
            hasUserHidden -> WalletCollectiblesState(items = emptyList(), allHidden = true)
            else -> null
        }
    }

    private fun applyNftPref(nft: NftEntity, pref: TokenPrefsEntity): NftEntity {
        if (pref.isTrust) {
            return nft.copy(trust = Trust.whitelist)
        }
        return nft
    }
}

private data class StakedLoad(
    val walletId: String,
    val currencyCode: String,
    val items: List<StakedUi>,
) {
    companion object {
        val Empty = StakedLoad(walletId = "", currencyCode = "", items = emptyList())
    }
}

private fun WalletTotal.plusNominatorStakes(stakes: List<StakedUi>): WalletTotal {
    val extra = stakes
        .asSequence()
        .filter { it.liquidAssetId == null }
        .fold(BigDecimal.ZERO) { acc, stake -> acc + stake.fiat }
    if (extra <= BigDecimal.ZERO) {
        return this
    }
    return copy(value = DisplayUnit(value.toDisplayValue() + extra, Decimal(2)))
}
