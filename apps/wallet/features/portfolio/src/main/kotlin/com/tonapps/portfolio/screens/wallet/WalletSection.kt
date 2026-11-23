package com.tonapps.portfolio.screens.wallet

import androidx.compose.runtime.Immutable
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.screens.portfolio.PerpsPositionUi
import com.tonapps.portfolio.wallet.WalletSectionLimit

@Immutable
sealed interface WalletSection {

    data object Assets : WalletSection

    sealed interface Perps : WalletSection {

        @Immutable
        data class Positions(
            val positions: List<PerpsPositionUi>,
            val balanceFormat: String?,
        ) : Perps

        @Immutable
        data class Shelf(
            val markets: List<PerpMarket>,
            val balanceFormat: String?,
        ) : Perps
    }

    sealed interface Earn : WalletSection {

        @Immutable
        data class Positions(
            val positions: List<EarnPositionUi>,
            val hiddenPositions: List<EarnPositionUi>,
            val limit: WalletSectionLimit,
            val totalFormat: String,
            val isStale: Boolean,
        ) : Earn

        data object Unavailable : Earn

        @Immutable
        data class Vaults(
            val vaults: List<EarnVaultUi>,
        ) : Earn
    }
}
