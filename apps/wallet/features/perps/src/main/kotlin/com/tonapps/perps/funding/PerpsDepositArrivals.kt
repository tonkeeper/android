package com.tonapps.perps.funding

import com.tonapps.blockchain.model.legacy.WalletType
import com.tonapps.perps.PerpsScreenTracker
import com.tonapps.perps.screens.depositstatus.PerpsDepositBannerState
import com.tonapps.wallet.data.multichain.account.UnifiedAccountRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PerpsDepositArrivals(
    private val recovery: PerpsFundingRecovery,
    private val unifiedAccountRepository: UnifiedAccountRepository,
    private val screenTracker: PerpsScreenTracker,
    private val scope: CoroutineScope,
) {

    val arrivals: SharedFlow<Unit> field = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun start() {
        scope.launch {
            unifiedAccountRepository.selectedTonWalletFlow
                .map { wallet -> wallet?.takeIf { it.type == WalletType.Multichain }?.id }
                .distinctUntilChanged()
                .collectLatest { walletId ->
                    if (walletId == null) {
                        return@collectLatest
                    }
                    val banner = PerpsDepositBannerState(isVisible = false)
                    recovery.observe(walletId).collect { records ->
                        val arrived = banner.apply(records.mapNotNull { it.toDepositStatus() })
                        if (arrived.isNotEmpty() && !screenTracker.isShowing(marketIndex = null)) {
                            arrivals.tryEmit(Unit)
                        }
                    }
                }
        }
    }
}
