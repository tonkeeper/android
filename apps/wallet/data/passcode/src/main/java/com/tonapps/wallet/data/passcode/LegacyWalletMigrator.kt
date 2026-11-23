package com.tonapps.wallet.data.passcode

import com.tonapps.security.multichain.MnemonicCoder

/**
 * Moves the wallets the old store holds into the multichain one. The lock screen drives it: the
 * recovery phrase is re-sealed under the vault, which only the passcode opens.
 */
interface LegacyWalletMigrator {

    /** Ids of the old-store wallets that can move; empty when there is nothing to do. */
    suspend fun migratableWalletIds(): List<String>

    /** Moves [walletIds] and returns the ids that moved. One wallet failing leaves it in the old store for the next start. */
    suspend fun migrate(walletIds: List<String>, coder: MnemonicCoder): List<String>
}
