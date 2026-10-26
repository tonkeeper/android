package com.tonapps.portfolio.screens.wallet

import androidx.compose.runtime.Immutable
import com.tonapps.perps.data.PerpMarket
import com.tonapps.perps.screens.portfolio.PerpsPositionUi

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
            val totalFormat: String,
        ) : Earn

        @Immutable
        data class Vaults(
            val vaults: List<EarnVaultUi>,
        ) : Earn
    }
}
