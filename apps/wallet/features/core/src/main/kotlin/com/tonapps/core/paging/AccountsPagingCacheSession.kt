package com.tonapps.core.paging

import com.tonapps.wallet.data.multichain.account.AccountsWithTotal
import java.util.concurrent.ConcurrentHashMap

class AccountsPagingCacheSession {
    private val prefetchedResults =
        ConcurrentHashMap<String, AccountsWithTotal>()

    fun setPrefetched(
        sessionKey: String,
        result: AccountsWithTotal,
    ) {
        prefetchedResults[sessionKey] = result
    }

    fun takePrefetched(
        sessionKey: String,
    ): AccountsWithTotal? {
        return prefetchedResults.remove(sessionKey)
    }
}
