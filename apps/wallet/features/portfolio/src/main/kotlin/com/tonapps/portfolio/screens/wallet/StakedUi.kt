package com.tonapps.portfolio.screens.wallet

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.tonapps.icu.Coins
import com.tonapps.wallet.data.staking.StakingPool

data class StakedUi(
    val poolAddress: String,
    val poolName: String,
    val poolImplementation: StakingPool.Implementation,
    val balanceFormat: CharSequence,
    val fiatFormat: CharSequence,
    val fiat: BigDecimal,
    val readyWithdraw: Coins = Coins.ZERO,
    val readyWithdrawFormat: CharSequence? = null,
    val pendingDeposit: Coins = Coins.ZERO,
    val pendingDepositFormat: CharSequence? = null,
    val pendingWithdraw: Coins = Coins.ZERO,
    val pendingWithdrawFormat: CharSequence? = null,
    val cycleEnd: Long = 0L,
    val liquidAssetId: String? = null,
    val hiddenBalance: Boolean,
)
