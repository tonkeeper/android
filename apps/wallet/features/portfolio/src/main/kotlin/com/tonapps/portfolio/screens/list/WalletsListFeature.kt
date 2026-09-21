package com.tonapps.portfolio.screens.list

import androidx.compose.runtime.mutableStateMapOf
import com.tonapps.core.flags.AddMcWalletTooltipInteractor
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.mvi.flow.flatMapLatestCatching
import com.tonapps.mvi.flow.mapLatestCatching
import com.tonapps.portfolio.wallet.CommonWallet
import com.tonapps.portfolio.domain.WalletFiatBalanceInteractor
import com.tonapps.portfolio.wallet.WalletLookup
import com.tonapps.wallet.api.entity.BannerEntity
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.banner.BannerRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.raffle.RaffleRepository
import com.tonapps.wallet.data.raffle.bannerEntity
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BALANCE_UNAVAILABLE = "—"
private const val MYSTERY_RAFFLE_BANNER_PREFIX = "mystery_raffle_"

@OptIn(ExperimentalCoroutinesApi::class)
class WalletsListFeature(
    private val accountRepoLegacy: AccountRepository,
    private val mcAccountRepo: McAccountRepository,
    private val walletLookup: WalletLookup,
    private val raffleRepository: RaffleRepository,
    private val bannerRepository: BannerRepository,
    private val settingsRepository: SettingsRepository,
    private val balanceInteractor: WalletFiatBalanceInteractor,
    private val addMcWalletTooltip: AddMcWalletTooltipInteractor,
) : AsyncViewModel() {

    private val walletsOrderChanged = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val wallets: StateFlow<List<CommonWallet>> = merge(
        mcAccountRepo.refreshTrigger,
        accountRepoLegacy.accountsChangedFlow,
        walletsOrderChanged,
    )
        .onStart { emit(Unit) }
        .mapLatestCatching {
            withContext(bgDispatcher) { walletLookup.getAllWallets() }
        }
        .filterNotNull()
        .cacheState(initialValue = emptyList())

    val banners: StateFlow<List<BannerEntity>> = accountRepoLegacy.selectedWalletFlow
        .flatMapLatestCatching { wallet ->
            val isMultichain = mcAccountRepo.getWallet(wallet.id) != null
            combine(
                raffleRepository.getRafflesFlow(wallet.id, forced = false),
                bannerRepository.getBannersFlow(
                    walletId = wallet.id,
                    isMultichain = isMultichain,
                ).onStart { emit(emptyList()) },
            ) { raffles, backendBanners ->
                val raffleBanners =
                    bannerRepository.filterHidden(wallet.id, raffles.mapNotNull { it.bannerEntity() })
                if (raffleBanners.isNotEmpty()) {
                    raffleBanners
                } else {
                    backendBanners.firstOrNull { it.id.startsWith(MYSTERY_RAFFLE_BANNER_PREFIX) }
                        ?.let(::listOf)
                        .orEmpty()
                }
            }
        }
        .cacheState(initialValue = emptyList())

    val hiddenBalances: StateFlow<Boolean> = settingsRepository.hiddenBalancesFlow

    val balances: Map<String, CharSequence> field = mutableStateMapOf<String, CharSequence>()

    val editMode: StateFlow<Boolean> field = MutableStateFlow(false)

    val selectedWalletId: StateFlow<String?> = accountRepoLegacy.selectedWalletFlow
        .map { it.id }
        .cacheState(initialValue = accountRepoLegacy.getSelectedWalletId())

    init {
        bgScope.launch {
            var lastCurrencyCode: String? = null
            combine(wallets, hiddenBalances, settingsRepository.currencyFlow) { wallets, hidden, currency ->
                val currencyChanged = lastCurrencyCode != null && lastCurrencyCode != currency.code
                lastCurrencyCode = currency.code
                Pair(if (hidden) { emptyList() } else { wallets }, currencyChanged)
            }.collectLatest { (list, currencyChanged) ->
                if (currencyChanged) { balances.clear() }
                loadBalances(list)
            }
        }
    }

    // Cache-first: the selected wallet's total is always fresh (the wallet screen just
    // loaded it), so the network is hit only for wallets with no cached total at all.
    private suspend fun loadBalances(wallets: List<CommonWallet>) {
        val missing = mutableListOf<CommonWallet>()
        for (wallet in wallets) {
            val cached = balanceInteractor.getCachedBalance(wallet)
            if (cached != null) {
                balances[wallet.id] = cached
            } else {
                missing += wallet
            }
        }
        coroutineScope {
            for (wallet in missing) {
                launch {
                    balances[wallet.id] = balanceInteractor.fetchBalance(wallet) ?: BALANCE_UNAVAILABLE
                }
            }
        }
    }

    fun toggleEditMode() {
        editMode.value = !editMode.value
    }

    fun saveWalletsOrder(walletIds: List<String>) {
        settingsRepository.setWalletsSort(walletIds)
        walletsOrderChanged.tryEmit(Unit)
    }

    fun selectWallet(walletId: String) {
        accountRepoLegacy.safeSetSelectedWallet(walletId)
    }

    suspend fun consumeAddMcTooltip(): Boolean =
        addMcWalletTooltip.consume(AddMcWalletTooltipInteractor.Placement.WALLETS_LIST)

    fun hideBanner(banner: BannerEntity) {
        val walletId = accountRepoLegacy.getSelectedWalletId() ?: return
        bgScope.launch {
            bannerRepository.hideBanner(walletId, banner.id)
        }
    }
}
