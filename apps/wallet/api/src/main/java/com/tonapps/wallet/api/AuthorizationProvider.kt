package com.tonapps.wallet.api

import com.tonapps.chainkit.core.chain.model.account.WalletKeyPair
import com.tonapps.wallet.api.entity.Authorization

interface AuthorizationProvider {

    suspend fun getAuthBy(walletId: String?): Authorization

    suspend fun getWalletKeyPair(walletId: String): WalletKeyPair?
}
