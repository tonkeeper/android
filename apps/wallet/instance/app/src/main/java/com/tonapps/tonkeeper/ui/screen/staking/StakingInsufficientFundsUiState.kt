package com.tonapps.tonkeeper.ui.screen.staking

import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.icu.Coins
import com.tonapps.icu.CurrencyFormatter
import com.tonapps.tonkeeper.ui.base.BaseWalletVM
import com.tonapps.wallet.data.staking.StakingPool
import com.tonapps.wallet.localization.Localization

data class StakingInsufficientFundsUiState(
    val requiredFormat: CharSequence,
    val balanceFormat: CharSequence,
    val reserveHint: String?,
)

// Whales keeps its withdrawal fee out of the reserve and returns only the rest.
fun BaseWalletVM.unstakeReserveHint(
    implementation: StakingPool.Implementation,
    reserve: Coins,
    isCollect: Boolean,
): String? {
    if (reserve.isZero) {
        return null
    }
    val reserveFormat = CurrencyFormatter.format(TokenEntity.TON.symbol, reserve)
    return if (implementation == StakingPool.Implementation.Whales) {
        val hint = if (isCollect) {
            Localization.collect_whales_fee_hint
        } else {
            Localization.unstaking_whales_fee_hint
        }
        getString(hint, reserveFormat)
    } else {
        getString(Localization.unstaking_reserve_returned_hint, reserveFormat)
    }
}
