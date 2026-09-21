package com.tonapps.core.flags

import com.tonapps.wallet.api.API
import com.tonapps.wallet.data.multichain.account.McAccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AddMcWalletTooltipInteractor(
    private val api: API,
    private val mcAccountRepository: McAccountRepository,
) {

    enum class Placement(val prefName: String) {
        MAIN("main"),
        WALLETS_LIST("wallets_list"),
    }

    suspend fun consume(placement: Placement): Boolean = withContext(Dispatchers.IO) {
        val key = WalletTooltipKey.ADD_MULTICHAIN_WALLET
        if (TooltipManager.shouldShowToday(key, placement.prefName) &&
            api.isMultichainAvailable() &&
            mcAccountRepository.getWalletsCount() == 0
        ) {
            TooltipManager.markShownToday(key, placement.prefName)
            true
        } else {
            false
        }
    }
}
