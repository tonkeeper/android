package com.tonapps.tonkeeper.ui.screen.staking

import com.tonapps.deposit.usecase.emulation.Emulated
import com.tonapps.icu.Coins

// Everything the wallet pays: total_fees holds only the action phase's share of the forward fee,
// the rest of it leaves the balance with the outgoing message.
val Emulated.walletFees: Coins
    get() = consequences?.let {
        val transaction = it.trace.transaction
        val forwardedFees = transaction.actionPhase?.let { phase -> phase.fwdFees - phase.totalFees } ?: 0L
        Coins.of(transaction.totalFees + forwardedFees)
    } ?: Coins.ZERO
