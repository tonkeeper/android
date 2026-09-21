package com.tonapps.tonkeeper.ui.screen.collectibles.main

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.tonapps.blockchain.model.legacy.WalletEntity
import com.tonapps.blockchain.ton.extensions.toRawAddress
import com.tonapps.extensions.flattenFirst
import com.tonapps.network.NetworkMonitor
import com.tonapps.tonkeeper.extensions.with
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.tonkeeper.ui.base.UiListState
import com.tonapps.tonkeeper.ui.screen.collectibles.main.list.Item
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.collectibles.CollectiblesRepository
import com.tonapps.wallet.data.collectibles.entities.DnsExpiringEntity
import com.tonapps.wallet.data.collectibles.entities.NftListResult
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import com.tonapps.wallet.data.tx.TransactionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import uikit.extensions.collectFlow

@OptIn(ExperimentalCoroutinesApi::class)
class CollectiblesViewModel(
    app: Application,
    private val accountRepository: AccountRepository,
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val mcAccountRepository: McAccountRepository,
    private val collectiblesRepository: CollectiblesRepository,
    private val networkMonitor: NetworkMonitor,
    private val settingsRepository: SettingsRepository,
    private val transactionManager: TransactionManager,
) : BaseWalletVM(app) {

    private val _ltFlow = MutableStateFlow(0L)
    private val ltFlow = _ltFlow.asStateFlow()

    val installId: String
        get() = settingsRepository.installId

    val walletFlow: StateFlow<WalletEntity?> = combine(
        accountRepository.selectedWalletFlow,
        settingsRepository.walletPrefsChangedFlow,
        mcAccountRepository.refreshTrigger,
    ) { selected, _, _ ->
        withContext(Dispatchers.IO) {
            unifiedAccountRepository.getTonWalletById(selected.id)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    private val triggerFlow = combine(
        settingsRepository.tokenPrefsChangedFlow,
        settingsRepository.safeModeChangedFlow,
        settingsRepository.hiddenBalancesFlow,
    ) { _, _, hiddenBalances -> hiddenBalances }

    val uiListStateFlow = combine(
        walletFlow.filterNotNull(),
        networkMonitor.isOnlineFlow,
    ) { wallet, isOnline ->
        wallet to isOnline
    }.flatMapLatest { (wallet, isOnline) ->
        stateFlow(
            wallet = wallet,
            isOnline = isOnline,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiListState.Loading)

    var hasNfts = false
        private set

    init {
        walletFlow.filterNotNull().flatMapLatest { wallet ->
            transactionManager.eventsFlow(wallet)
        }.collectFlow {
            _ltFlow.value = it.lt
        }
    }

    private fun stateFlow(
        wallet: WalletEntity,
        isOnline: Boolean,
    ): Flow<UiListState> {
        val expiringDomainsFlow = flow {
            emit(emptyMap<String, DnsExpiringEntity>())
            val expiring = try {
                collectiblesRepository.getDnsSoonExpiring(
                    accountId = wallet.accountId,
                    network = wallet.network,
                ).associateBy { it.addressRaw }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
            if (!expiring.isNullOrEmpty()) {
                emit(expiring)
            }
        }

        val nftItemsFlow = ltFlow
            .map { collectiblesRepository.getFlow(wallet.address, wallet.network, isOnline) }
            .flattenFirst()

        return combine(
            nftItemsFlow,
            expiringDomainsFlow,
            triggerFlow,
        ) { result, expiringDomains, hiddenBalances ->
            mapItems(wallet, hiddenBalances, result, expiringDomains)
        }.onStart { emit(UiListState.Loading) }
            .flowOn(Dispatchers.IO)
    }

    private suspend fun mapItems(
        wallet: WalletEntity,
        hiddenBalances: Boolean,
        result: NftListResult,
        expiringDomains: Map<String, DnsExpiringEntity>,
    ): UiListState {
        hasNfts = result.list.isNotEmpty()
        val safeMode = settingsRepository.isSafeModeEnabled(wallet.id, wallet.network)
        val uiItems = mutableListOf<Item>()
        for (nft in result.list) {
            if (safeMode && !nft.verified) {
                continue
            }

            val nftPref = settingsRepository.getTokenPrefs(wallet.id, nft.collectionAddressOrNFTAddress)
            if (nftPref.isHidden) {
                continue
            }
            val expiringDomain = expiringDomains[nft.address.toRawAddress()]
            uiItems.add(
                Item.Nft(
                    wallet = wallet,
                    entity = nft.with(nftPref),
                    hiddenBalance = hiddenBalances,
                    expiringDomainSoon = expiringDomain != null,
                ),
            )
        }

        return if (uiItems.isEmpty() && !result.cache) {
            UiListState.Empty
        } else if (uiItems.isEmpty()) {
            UiListState.Loading
        } else {
            UiListState.Items(result.cache, uiItems.toList())
        }
    }

    fun refresh() {
        _ltFlow.value += 1
    }
}
