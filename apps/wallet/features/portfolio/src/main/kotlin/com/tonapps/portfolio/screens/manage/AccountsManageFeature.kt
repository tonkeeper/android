package com.tonapps.portfolio.screens.manage

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import com.tonapps.chainkit.core.chain.model.account.Network
import com.tonapps.log.L
import com.tonapps.mvi.AsyncViewModel
import com.tonapps.core.paging.AccountsPagingCacheSession
import com.tonapps.core.paging.AccountsPagingSource
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AccountsManageFeature(
    private val accountRepo: McAccountRepository,
    private val accountRepoLegacy: AccountRepository,
    private val settingsRepository: SettingsRepository,
) : AsyncViewModel() {

    val queryFilter: StateFlow<String> field = MutableStateFlow("")
    val networkFilter: StateFlow<Network.Type?> field = MutableStateFlow(null)
    val hiddenOverrides: StateFlow<Map<String, Boolean>> field = MutableStateFlow(emptyMap())
    val pendingVisibilityChanges: StateFlow<Map<String, Boolean>> field = MutableStateFlow(emptyMap())
    val isSavingVisibility: StateFlow<Boolean> field = MutableStateFlow(false)
    val hideDust: StateFlow<Boolean> = settingsRepository.hideDustAssetsFlow

    private val accountsPagingCacheSession = AccountsPagingCacheSession()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val accountsFlow = combine(
        queryFilter.debounce(300),
        networkFilter,
        settingsRepository.currencyFlow,
        settingsRepository.hideDustAssetsFlow,
    ) { query, network, walletCurrency, _ ->
        Triple(query, network, walletCurrency.code)
    }
        .flatMapLatest { (query, network, currencyCode) ->
            val walletId = accountRepoLegacy.getSelectedWalletId().orEmpty()
            val hideDust = settingsRepository.hideDustAssets
            Pager(
                config = PagingConfig(
                    initialLoadSize = 25,
                    pageSize = 25,
                    prefetchDistance = 5,
                    enablePlaceholders = false,
                ),
                pagingSourceFactory = {
                    AccountsPagingSource(
                        accountRepo = accountRepo,
                        walletId = walletId,
                        currency = currencyCode,
                        query = query.takeIf { it.isNotBlank() },
                        network = network?.id,
                        showHidden = true,
                        showAll = true,
                        hideDust = hideDust,
                        cacheSession = accountsPagingCacheSession,
                        scope = viewModelScope,
                    )
                },
            ).flow
        }
        .cachedIn(viewModelScope)

    fun onSearch(query: String) {
        if (query == queryFilter.value) {
            return
        }
        queryFilter.tryEmit(query)
        resetPendingEdits()
    }

    fun onNetworkSelected(network: Network.Type?) {
        if (network == networkFilter.value) {
            return
        }
        networkFilter.tryEmit(network)
        resetPendingEdits()
    }

    fun onHideDustChanged() {
        settingsRepository.hideDustAssets = !settingsRepository.hideDustAssets
        resetPendingEdits()
    }

    private fun resetPendingEdits() {
        hiddenOverrides.tryEmit(emptyMap())
        pendingVisibilityChanges.tryEmit(emptyMap())
    }

    fun toggleVisibility(assetId: String, displayedIsHidden: Boolean, serverIsHidden: Boolean) {
        val newHidden = !displayedIsHidden
        hiddenOverrides.update { it + (assetId to newHidden) }
        val desiredVisible = !newHidden
        val serverVisible = !serverIsHidden
        pendingVisibilityChanges.update { prev ->
            val next = prev.toMutableMap()
            if (desiredVisible == serverVisible) {
                next.remove(assetId)
            } else {
                next[assetId] = desiredVisible
            }
            next
        }
    }

    fun savePendingVisibilityChanges(onDone: () -> Unit) {
        val pending = pendingVisibilityChanges.value

        if (pending.isEmpty()) {
            return
        }

        bgScope.launch {
            isSavingVisibility.emit(true)
            try {
                val walletId = accountRepoLegacy.getSelectedWalletId().orEmpty()
                accountRepo.setAssetsVisibility(walletId, pending)
                pendingVisibilityChanges.tryEmit(emptyMap())
                hiddenOverrides.tryEmit(emptyMap())
                onDone()
            } catch (e: Exception) {
                L.e(e)
            } finally {
                isSavingVisibility.emit(false)
            }
        }
    }
}
