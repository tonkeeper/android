package com.tonapps.migration.data

import com.tonapps.icu.Coins
import com.tonapps.wallet.data.multichain.wallet.McWalletEntity
import io.tonapi.models.MigrationPrepareResponse
import io.tonapi.models.MigrationTransaction

data class MigrationPrepareResult(
    val wallet: MigratableWallet,
    val response: MigrationPrepareResponse?,
    val destinationWallet: McWalletEntity,
    val tronPrepare: MigrationTronPrepare = MigrationTronPrepare.Empty,
    val tonFee: MigrationTonFee = MigrationTonFee.None,
    val tonFeeOptions: MigrationTonFeeOptions = MigrationTonFeeOptions.Empty,
    val requiredTonNano: Long? = null,
    val availableTonNano: Long? = null,
) {
    val transactions: List<MigrationTransaction>
        get() = tonFeeOptions.transactionsFor(tonFee)
            .ifEmpty { response?.transactions.orEmpty() }

    val tonFeeNano: Long = requiredTonNano ?: transactions.selfPaidGasNano()

    val paysTonFeeWithBattery: Boolean = tonFee is MigrationTonFee.Battery

    val totalEquivalent: Float? = run {
        val tonValues = transactions.mapNotNull { it.emulation.risk.totalEquivalent }
        val ton = tonValues.takeIf { it.isNotEmpty() }?.sum() ?: 0f
        val tron = tronPrepare.totalFiat.value.toFloat()
        val sum = ton + tron
        sum.takeIf {
            tonValues.isNotEmpty() || tronPrepare.hasTransfers
        }
    }

    val nftCount: Int = transactions
        .flatMap { it.emulation.risk.nfts }
        .distinctBy { it.address }
        .size

    fun withTonFee(fee: MigrationTonFee): MigrationPrepareResult {
        if (fee == tonFee) return this
        return copy(tonFee = fee)
    }

    fun withTronFee(
        fee: MigrationTronFee,
        trxFiatOf: (Coins) -> Coins,
    ): MigrationPrepareResult {
        if (fee == tronPrepare.fee) return this
        return copy(
            tronPrepare = tronPrepare.withFee(
                fee = fee,
                availableTrx = wallet.tronBalances.migratableTrx,
                trxFiatOf = trxFiatOf,
            ),
        )
    }

    fun requiredBatteryCharges(
        tonFee: MigrationTonFee = this.tonFee,
        tronFee: MigrationTronFee = tronPrepare.fee,
    ): Int {
        val tonCharges = (tonFee as? MigrationTonFee.Battery)?.charges ?: 0
        val tronCharges = (tronFee as? MigrationTronFee.Battery)?.charges ?: 0
        return tonCharges + tronCharges
    }

    fun isTonFeePayable(fee: MigrationTonFee): Boolean {
        if (!tonFeeOptions.isEnough(fee)) return false
        if (fee !is MigrationTonFee.Battery) return true
        if (tonFeeOptions.batteryTransactions.none { it.sponsored == true }) return false
        return tonFeeOptions.availableCharges >= requiredBatteryCharges(
            tonFee = fee,
            tronFee = tronPrepare.fee,
        )
    }

    fun isTronFeePayable(fee: MigrationTronFee): Boolean {
        if (!tronPrepare.feeOptions.isEnough(fee)) return false
        if (fee !is MigrationTronFee.Battery) return true
        return tonFeeOptions.availableCharges >= requiredBatteryCharges(
            tonFee = tonFee,
            tronFee = fee,
        )
    }

    fun canSelectTonFee(fee: MigrationTonFee): Boolean {
        if (!tonFeeOptions.isEnough(fee)) return false
        if (fee !is MigrationTonFee.Battery) return true
        if (tonFeeOptions.batteryTransactions.none { it.sponsored == true }) return false
        val tronFee = tronPrepare.fee
        if (tronFee !is MigrationTronFee.Battery) return true
        if (tonFeeOptions.availableCharges >= requiredBatteryCharges(fee, tronFee)) return true
        return tronPrepare.feeOptions.trxEnough
    }

    fun canSelectTronFee(fee: MigrationTronFee): Boolean {
        if (!tronPrepare.feeOptions.isEnough(fee)) return false
        if (fee !is MigrationTronFee.Battery) return true
        val currentTon = tonFee
        if (currentTon !is MigrationTonFee.Battery) return true
        if (tonFeeOptions.availableCharges >= requiredBatteryCharges(currentTon, fee)) return true
        return tonFeeOptions.tonEnough
    }
}

internal fun MigrationPrepareResponse.selfPaidGasNano(): Long = transactions.selfPaidGasNano()

internal fun MigrationPrepareResponse.totalGasSpentNano(): Long = transactions.totalGasSpentNano()

internal fun MigrationPrepareResponse.sponsoredGasSpentNano(): Long = transactions.sponsoredGasSpentNano()

internal fun MigrationPrepareResponse.batteryChargeBasisNano(): Long = transactions.batteryChargeBasisNano()

internal fun List<MigrationTransaction>.selfPaidGasNano(): Long =
    filter { it.sponsored != true }.sumOf { it.gasSpent }

internal fun List<MigrationTransaction>.totalGasSpentNano(): Long = sumOf { it.gasSpent }

internal fun List<MigrationTransaction>.sponsoredGasSpentNano(): Long =
    filter { it.sponsored == true }.sumOf { it.gasSpent }

internal fun List<MigrationTransaction>.batteryChargeBasisNano(): Long {
    val sponsored = filter { it.sponsored == true }
    if (sponsored.isEmpty()) return 0L

    val fromGasSpent = sponsored.sumOf { it.gasSpent }
    if (fromGasSpent > 0L) return fromGasSpent

    val fromExtra = sponsored.sumOf { transaction ->
        val extra = transaction.emulation.event.extra
        if (extra < 0) kotlin.math.abs(extra) else 0L
    }
    if (fromExtra > 0L) return fromExtra

    return sumOf { it.gasSpent }
}
