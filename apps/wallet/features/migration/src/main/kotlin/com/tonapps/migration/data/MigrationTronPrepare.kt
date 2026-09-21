package com.tonapps.migration.data

import com.tonapps.blockchain.tron.TronTransfer
import com.tonapps.icu.Coins
import com.tonapps.wallet.api.tron.entity.TronResourcesEntity

data class MigrationTronPrepare(
    val fromAddress: String,
    val toAddress: String,
    val usdtAmount: Coins = Coins.ZERO,
    val usdtFiat: Coins = Coins.ZERO,
    val trxAmount: Coins = Coins.ZERO,
    val trxFiat: Coins = Coins.ZERO,
    val usdtTransfer: TronTransfer? = null,
    val usdtResources: TronResourcesEntity? = null,
    val trxResources: TronResourcesEntity? = null,
    val fee: MigrationTronFee = MigrationTronFee.None,
    val feeOptions: MigrationUsdtFeeOptions = MigrationUsdtFeeOptions.Empty,
    val usdtRequiredTrx: Coins = Coins.ZERO,
    val nativeRequiredTrx: Coins = Coins.ZERO,
) {
    val hasTransfers: Boolean
        get() = usdtAmount.isPositive || trxAmount.isPositive

    val willSendUsdt: Boolean
        get() = usdtAmount.isPositive

    val willSweepTrx: Boolean
        get() = trxAmount.isPositive

    val stepCount: Int
        get() = listOf(willSendUsdt, willSweepTrx).count { it }

    val hasUsdtFee: Boolean
        get() = fee !is MigrationTronFee.None

    val totalFiat: Coins
        get() = usdtFiat + trxFiat

    fun reservedTrx(feeMethod: MigrationTronFee = fee): Coins {
        return when (feeMethod) {
            is MigrationTronFee.Battery -> {
                // USDT battery still leaves a self-paid TRX sweep that may burn bandwidth.
                if (willSendUsdt) nativeRequiredTrx else Coins.ZERO
            }
            is MigrationTronFee.Trx -> {
                nativeRequiredTrx + if (willSendUsdt) usdtRequiredTrx else Coins.ZERO
            }
            is MigrationTronFee.Ton,
            MigrationTronFee.None,
            -> nativeRequiredTrx
        }
    }

    fun trxTransferAmount(feeMethod: MigrationTronFee = fee, availableTrx: Coins): Coins {
        val reserved = reservedTrx(feeMethod)
        val remaining = availableTrx - reserved
        return if (remaining.isPositive) remaining else Coins.ZERO
    }

    fun withFee(
        fee: MigrationTronFee,
        availableTrx: Coins,
        trxFiatOf: (Coins) -> Coins,
    ): MigrationTronPrepare {
        val amount = trxTransferAmount(feeMethod = fee, availableTrx = availableTrx)
            .withoutMigrationDust()
        return copy(
            fee = fee,
            trxAmount = amount,
            trxFiat = if (amount.isPositive) trxFiatOf(amount) else Coins.ZERO,
        )
    }

    companion object {
        val Empty = MigrationTronPrepare(fromAddress = "", toAddress = "")
    }
}

data class MigrationUsdtFeeOptions(
    val battery: MigrationTronFee.Battery? = null,
    val batteryEnough: Boolean = false,
    val trx: MigrationTronFee.Trx? = null,
    val trxEnough: Boolean = false,
) {
    val methods: List<MigrationTronFee>
        get() = listOfNotNull(trx, battery)

    val canSwitchFee: Boolean
        get() = methods.size > 1

    fun isEnough(fee: MigrationTronFee): Boolean = when (fee) {
        is MigrationTronFee.Trx -> trxEnough
        is MigrationTronFee.Battery -> batteryEnough
        is MigrationTronFee.Ton,
        MigrationTronFee.None,
        -> false
    }

    companion object {
        val Empty = MigrationUsdtFeeOptions()
    }
}

sealed interface MigrationTronFee {
    data object None : MigrationTronFee

    data class Battery(
        val charges: Int,
        val fiatAmount: Coins,
    ) : MigrationTronFee

    data class Ton(
        val amount: Coins,
        val fiatAmount: Coins,
    ) : MigrationTronFee

    data class Trx(
        val amount: Coins,
        val fiatAmount: Coins,
    ) : MigrationTronFee
}
