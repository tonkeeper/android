package com.tonapps.core.flags

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EarnTooltipInteractor {

    suspend fun canShow(): Boolean = withContext(Dispatchers.IO) {
        TooltipManager.shouldShowToday(WalletTooltipKey.EARN_SECTION, PLACEMENT)
    }

    suspend fun markShown() {
        withContext(Dispatchers.IO) {
            TooltipManager.markShownToday(WalletTooltipKey.EARN_SECTION, PLACEMENT)
        }
    }

    private companion object {
        const val PLACEMENT = "main"
    }
}
