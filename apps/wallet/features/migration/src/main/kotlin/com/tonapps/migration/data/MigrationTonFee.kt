package com.tonapps.migration.data

import com.tonapps.icu.Coins
import io.tonapi.models.MigrationTransaction

sealed interface MigrationTonFee {

    val tonAmount: Coins

    data object None : MigrationTonFee {
        override val tonAmount: Coins = Coins.ZERO
    }

    data class Ton(
        override val tonAmount: Coins,
    ) : MigrationTonFee

    data class Battery(
        val charges: Int,
        override val tonAmount: Coins,
    ) : MigrationTonFee
}

data class MigrationTonFeeOptions(
    val ton: MigrationTonFee.Ton? = null,
    val tonEnough: Boolean = false,
    val tonTransactions: List<MigrationTransaction> = emptyList(),
    val battery: MigrationTonFee.Battery? = null,
    val batteryEnough: Boolean = false,
    val batteryTransactions: List<MigrationTransaction> = emptyList(),
    val availableCharges: Int = 0,
) {
    val methods: List<MigrationTonFee>
        get() = listOfNotNull(ton, battery)

    val canSwitchFee: Boolean
        get() = methods.size > 1

    fun transactionsFor(fee: MigrationTonFee): List<MigrationTransaction> = when (fee) {
        is MigrationTonFee.Ton -> tonTransactions
        is MigrationTonFee.Battery -> batteryTransactions
        MigrationTonFee.None -> emptyList()
    }

    fun isEnough(fee: MigrationTonFee): Boolean = when (fee) {
        is MigrationTonFee.Ton -> tonEnough
        is MigrationTonFee.Battery -> batteryEnough
        MigrationTonFee.None -> false
    }

    companion object {
        val Empty = MigrationTonFeeOptions()
    }
}
