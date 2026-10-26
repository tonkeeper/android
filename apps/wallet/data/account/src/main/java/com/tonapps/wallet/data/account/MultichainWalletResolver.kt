package com.tonapps.wallet.data.account

import com.tonapps.blockchain.model.legacy.WalletEntity

interface MultichainWalletResolver {

    // null means there is no such wallet; LookupException means the lookup itself failed.
    class LookupException(cause: Throwable) : Exception(cause)

    @Throws(LookupException::class)
    suspend fun getTonWallet(id: String): WalletEntity?

    @Throws(LookupException::class)
    suspend fun getDefaultTonWallet(): WalletEntity?
}
