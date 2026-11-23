package com.tonapps.tonkeeper.extensions

import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val SELECTION_TIMEOUT_MS = 5_000L

suspend fun UnifiedAccountRepository.awaitSelectedTonWallet(walletId: String): Boolean {
    val selected = withTimeoutOrNull(SELECTION_TIMEOUT_MS) {
        selectedTonWalletFlow.first { it?.id == walletId }
    }
    return selected != null
}
