package com.tonapps.perps.trade

import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PerpsOperationRecovery(
    private val executor: PerpsTradeExecutor,
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val scope: CoroutineScope,
) {

    fun start() {
        scope.launch {
            unifiedAccountRepository.selectedTonWalletFlow
                .map { wallet -> wallet?.takeIf { it.type == WalletType.Multichain }?.id }
                .filterNotNull()
                .distinctUntilChanged()
                .collectLatest { walletId -> executor.reconcile(walletId) }
        }
    }
}
