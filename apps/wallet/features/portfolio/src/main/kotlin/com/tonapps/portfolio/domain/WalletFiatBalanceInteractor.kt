package com.tonapps.portfolio.domain

import androidx.annotation.AnyThread
import com.tonapps.portfolio.wallet.CommonWallet

/**
 * Total fiat balance of a wallet, formatted for display in the wallets list.
 *
 * Legacy wallet totals come from AssetsManager, multichain totals from McAccountRepository.
 *
 * Main-safe: implementations must switch to an IO dispatcher internally.
 */
@AnyThread
interface WalletFiatBalanceInteractor {

    /** Returns the locally cached total, or null when nothing is cached yet. */
    suspend fun getCachedBalance(wallet: CommonWallet): CharSequence?

    /** Fetches the total from the network, or null when the request fails. */
    suspend fun fetchBalance(wallet: CommonWallet, refresh: Boolean = false): CharSequence?
}
