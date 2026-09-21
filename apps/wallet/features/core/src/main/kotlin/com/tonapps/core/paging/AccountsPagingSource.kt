package com.tonapps.core.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.AssetCapability
import com.tonapps.wallet.data.multichain.account.AccountsWithTotal
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import com.tonapps.wallet.data.multichain.account.walletAssetsCacheKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class AccountsPagingSource(
    private val accountRepo: McAccountRepository,
    private val walletId: String,
    private val currency: String,
    private val query: String? = null,
    private val network: String? = null,
    private val availableOnly: Boolean = false,
    private val showHidden: Boolean = false,
    private val showAll: Boolean = false,
    private val capabilities: List<AssetCapability>? = null,
    private val verifiedOnly: Boolean = false,
    private val hideDust: Boolean = false,
    private val cacheSession: AccountsPagingCacheSession,
    private val scope: CoroutineScope,
    private val onTotalLoaded: ((AccountsWithTotal) -> Unit)? = null,
    private val onLoadError: ((Throwable) -> Unit)? = null,
) : PagingSource<String, AccountWithDetails>() {

    private val sessionKey = buildSessionKey()

    private val loadedAssetIds = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var refreshJob: Job? = null

    init {
        registerInvalidatedCallback {
            refreshJob?.cancel()
            refreshJob = null
        }
    }

    override suspend fun load(
        params: LoadParams<String>,
    ): LoadResult<String, AccountWithDetails> = withContext(Dispatchers.IO) {
        try {
            loadInternal(params)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadFallback(
                params = params,
                error = e,
            )
        }
    }

    private suspend fun loadInternal(
        params: LoadParams<String>,
    ): LoadResult<String, AccountWithDetails> {
        if (params is LoadParams.Refresh) {
            cacheSession.takePrefetched(sessionKey)?.let { prefetched ->
                notifyTotalLoaded(prefetched)
                return prefetched.toPage()
            }

            getCachedAccountsOrNull()?.let { cached ->
                notifyTotalLoaded(cached)
                refreshFromNetwork(
                    pageSize = params.loadSize,
                )
                return cached.toPage(
                    nextKey = null,
                )
            }
        }

        val result = fetchAccounts(
            cursor = params.key,
            limit = params.loadSize,
        )

        if (params is LoadParams.Refresh) {
            notifyTotalLoaded(result)
        }

        return result.toPage()
    }

    private suspend fun loadFallback(
        params: LoadParams<String>,
        error: Exception,
    ): LoadResult<String, AccountWithDetails> {
        if (params is LoadParams.Refresh) {
            getCachedAccountsOrNull()?.let { cached ->
                notifyTotalLoaded(cached)
                return cached.toPage(
                    nextKey = null,
                )
            }
        }

        onLoadError?.invoke(error)
        return LoadResult.Error(error)
    }

    private fun refreshFromNetwork(pageSize: Int) {
        if (invalid || refreshJob?.isActive == true) {
            return
        }

        refreshJob = scope.launch(Dispatchers.IO) {
            try {
                val previous = getCachedAccountsOrNull()
                val result = fetchAccounts(
                    cursor = null,
                    limit = pageSize,
                )

                if (invalid) {
                    return@launch
                }

                if (previous == result) {
                    return@launch
                }

                cacheSession.setPrefetched(
                    sessionKey = sessionKey,
                    result = result,
                )

                invalidate()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (invalid || !isActive) {
                    return@launch
                }
                onLoadError?.invoke(e)
            }
        }
    }

    private suspend fun getCachedAccountsOrNull(): AccountsWithTotal? {
        return try {
            accountRepo.getCachedAccounts(
                walletId = walletId,
                currency = currency,
                query = query,
                network = network,
                availableOnly = availableOnly,
                showHidden = showHidden,
                showAll = showAll,
                capabilities = capabilities,
                verifiedOnly = verifiedOnly,
                hideDust = hideDust,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchAccounts(
        cursor: String?,
        limit: Int,
    ): AccountsWithTotal {
        return accountRepo.fetchAccounts(
            walletId = walletId,
            currency = currency,
            query = query,
            network = network,
            cursor = cursor,
            limit = limit,
            availableOnly = availableOnly,
            showHidden = showHidden,
            showAll = showAll,
            capabilities = capabilities,
            verifiedOnly = verifiedOnly,
            hideDust = hideDust,
        )
    }

    private fun notifyTotalLoaded(result: AccountsWithTotal) {
        onTotalLoaded?.invoke(result)
    }

    private fun AccountsWithTotal.toPage(
        nextKey: String? = nextCursor,
    ): LoadResult.Page<String, AccountWithDetails> {
        return LoadResult.Page(
            data = accounts.dropAlreadyLoaded(),
            prevKey = null,
            nextKey = nextKey,
        )
    }

    private fun List<AccountWithDetails>.dropAlreadyLoaded(): List<AccountWithDetails> =
        filter { loadedAssetIds.add(it.asset.id) }

    private fun buildSessionKey(): String = walletAssetsCacheKey(
        walletId = walletId,
        currency = currency,
        availableOnly = availableOnly,
        showHidden = showHidden,
        showAll = showAll,
        query = query,
        network = network,
        capabilities = capabilities,
        verifiedOnly = verifiedOnly,
        hideDust = hideDust,
    )

    override fun getRefreshKey(
        state: PagingState<String, AccountWithDetails>,
    ): String? = null
}
