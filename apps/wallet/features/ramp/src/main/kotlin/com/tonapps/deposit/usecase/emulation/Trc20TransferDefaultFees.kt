package com.tonapps.deposit.usecase.emulation

import com.tonapps.icu.Coins
import com.tonapps.blockchain.model.legacy.WalletCurrency

data class Trc20TransferDefaultFees(
    val batteryFee: BatteryFee,
    val tonFee: TonFee,
    val trxFee: TrxFee,
    val currency: WalletCurrency,
    val totalAvailableTransfers: Int
) {
    data class BatteryFee(
        val balance: Int,
        val charges: Int,
        val fiatAmount: Coins,
        val availableTransfers: Int
    )

    data class TonFee(
        val balance: Coins,
        val amount: Coins,
        val fiatAmount: Coins,
        val availableTransfers: Int
    )

    data class TrxFee(
        val balance: Coins,
        val amount: Coins,
        val fiatAmount: Coins,
        val availableTransfers: Int
    )
}
