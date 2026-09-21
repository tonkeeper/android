package com.tonapps.tonkeeper.ui.screen.wallet.main

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * In-memory, session-scoped store of which wallets have their assets list expanded.
 * Intentionally NOT persisted: a fresh app session always starts collapsed.
 */
object AssetsExpandState {

    private val expandedWalletIds = MutableStateFlow<Set<String>>(emptySet())

    fun flow(walletId: String): Flow<Boolean> = expandedWalletIds
        .map { it.contains(walletId) }
        .distinctUntilChanged()

    fun expand(walletId: String) {
        expandedWalletIds.value = expandedWalletIds.value + walletId
    }

    fun reset(walletId: String) {
        expandedWalletIds.value = expandedWalletIds.value - walletId
    }
}
