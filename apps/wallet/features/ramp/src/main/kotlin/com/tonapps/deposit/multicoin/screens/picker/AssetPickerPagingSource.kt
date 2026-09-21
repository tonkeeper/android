package com.tonapps.deposit.multicoin.screens.picker

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.tonapps.wallet.data.account.AccountRepository
import com.tonapps.wallet.data.multichain.account.AccountWithDetails
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

internal class AssetPickerPagingSource(
    private val oldAccount: AccountRepository,
    private val accountRepo: McAccountRepository,
    private val currency: String,
    private val query: String?,
    private val network: String?,
    private val onFirstPageLoaded: (List<AccountWithDetails>) -> Unit = {},
) : PagingSource<String, AccountWithDetails>() {

    private val loadedAssetIds = ConcurrentHashMap.newKeySet<String>()

    override suspend fun load(
        params: LoadParams<String>
    ): LoadResult<String, AccountWithDetails> = withContext(Dispatchers.IO) {
        try {
            val walletId = oldAccount.getSelectedWalletId()
                ?: return@withContext LoadResult.Page(emptyList(), null, null)

            val page = accountRepo.fetchAccounts(
                walletId = walletId,
                currency = currency,
                query = query,
                network = network,
                cursor = params.key,
                limit = params.loadSize,
                availableOnly = true,
            )

            val accounts = page.accounts.dropAlreadyLoaded()
            if (params.key == null) {
                onFirstPageLoaded(accounts)
            }

            LoadResult.Page(
                data = accounts,
                prevKey = null,
                nextKey = page.nextCursor,
            )
        } catch (e: Throwable) {
            LoadResult.Error(e)
        }
    }

    private fun List<AccountWithDetails>.dropAlreadyLoaded(): List<AccountWithDetails> =
        filter { loadedAssetIds.add(it.asset.id) }

    override fun getRefreshKey(state: PagingState<String, AccountWithDetails>) = null
}
