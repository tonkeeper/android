package com.tonapps.migration.data

import com.tonapps.blockchain.model.legacy.TokenEntity
import com.tonapps.core.extensions.formatFullWithSymbol
import com.tonapps.core.extensions.formatWithSymbol
import com.tonapps.icu.Coins

sealed interface MigrationFeeShortage {
    val walletEmoji: CharSequence
    val walletName: String

    data class Ton(
        override val walletEmoji: CharSequence,
        override val walletName: String,
        val required: Coins,
        val balance: Coins,
        val canContinueWithTron: Boolean,
    ) : MigrationFeeShortage

    data class Trx(
        override val walletEmoji: CharSequence,
        override val walletName: String,
        val required: Coins,
        val balance: Coins,
        val canContinueWithTon: Boolean,
    ) : MigrationFeeShortage

    data class Both(
        override val walletEmoji: CharSequence,
        override val walletName: String,
        val requiredTon: Coins,
        val balanceTon: Coins,
        val requiredTrx: Coins,
        val balanceTrx: Coins,
    ) : MigrationFeeShortage

    data class Battery(
        override val walletEmoji: CharSequence,
        override val walletName: String,
        val requiredCharges: Int,
        val availableCharges: Int,
        val canContinueWithTon: Boolean,
        val canContinueWithTron: Boolean,
    ) : MigrationFeeShortage
}

fun MigrationPrepareResult.resolveFeeShortage(): MigrationFeeShortage? {
    val tonBalance = availableTonNano?.let { Coins.of(it) } ?: wallet.tonBalance
    val trxBalance = wallet.tronBalances.trx
    val label = wallet.wallet.label
    val availableCharges = tonFeeOptions.availableCharges

    val requiredTon = if (requiredTonNano != null || (transactions.isNotEmpty() && !paysTonFeeWithBattery)) {
        Coins.of(tonFeeNano)
    } else {
        Coins.ZERO
    }
    val insufficientTon = requiredTonNano != null || (requiredTon.isPositive && tonBalance < requiredTon)

    val requiredTrx = (tronPrepare.fee as? MigrationTronFee.Trx)?.amount ?: Coins.ZERO
    val insufficientTrx = requiredTrx.isPositive && trxBalance < requiredTrx

    val requiredCharges = requiredBatteryCharges()
    val insufficientBattery = requiredCharges > 0 && availableCharges < requiredCharges

    if (!insufficientTon && !insufficientTrx && !insufficientBattery) return null

    val canContinueWithTon = isTonLegPayableAlone()
    val canContinueWithTron = isTronLegPayableAlone()

    if (insufficientBattery && !insufficientTon && !insufficientTrx) {
        val softTon = canContinueWithTon
        val softTron = canContinueWithTron && !softTon
        return MigrationFeeShortage.Battery(
            walletEmoji = label.emoji,
            walletName = label.name,
            requiredCharges = requiredCharges,
            availableCharges = availableCharges,
            canContinueWithTon = softTon,
            canContinueWithTron = softTron,
        )
    }

    return when {
        insufficientTon && insufficientTrx -> MigrationFeeShortage.Both(
            walletEmoji = label.emoji,
            walletName = label.name,
            requiredTon = requiredTon,
            balanceTon = tonBalance,
            requiredTrx = requiredTrx,
            balanceTrx = trxBalance,
        )

        insufficientTon -> MigrationFeeShortage.Ton(
            walletEmoji = label.emoji,
            walletName = label.name,
            required = requiredTon,
            balance = tonBalance,
            canContinueWithTron = canContinueWithTron,
        )

        insufficientBattery && insufficientTrx && !canContinueWithTon -> MigrationFeeShortage.Battery(
            walletEmoji = label.emoji,
            walletName = label.name,
            requiredCharges = requiredCharges,
            availableCharges = availableCharges,
            canContinueWithTon = false,
            canContinueWithTron = false,
        )

        insufficientTrx -> MigrationFeeShortage.Trx(
            walletEmoji = label.emoji,
            walletName = label.name,
            required = requiredTrx,
            balance = trxBalance,
            canContinueWithTon = canContinueWithTon,
        )

        else -> MigrationFeeShortage.Battery(
            walletEmoji = label.emoji,
            walletName = label.name,
            requiredCharges = requiredCharges,
            availableCharges = availableCharges,
            canContinueWithTon = canContinueWithTon,
            canContinueWithTron = canContinueWithTron && !canContinueWithTon,
        )
    }
}

private fun MigrationPrepareResult.isTonLegPayableAlone(): Boolean {
    if (transactions.isEmpty()) return false
    return when (val fee = tonFee) {
        is MigrationTonFee.Ton -> tonFeeOptions.tonEnough
        is MigrationTonFee.Battery -> {
            tonFeeOptions.batteryEnough && tonFeeOptions.availableCharges >= fee.charges
        }
        MigrationTonFee.None -> false
    }
}

private fun MigrationPrepareResult.isTronLegPayableAlone(): Boolean {
    if (!tronPrepare.hasTransfers) return false
    return when (val fee = tronPrepare.fee) {
        is MigrationTronFee.Trx -> tronPrepare.feeOptions.trxEnough
        is MigrationTronFee.Battery -> {
            tronPrepare.feeOptions.batteryEnough && tonFeeOptions.availableCharges >= fee.charges
        }
        is MigrationTronFee.Ton -> false
        MigrationTronFee.None -> true
    }
}

fun MigrationFeeShortage.formatRequired(): String = when (this) {
    is MigrationFeeShortage.Ton -> required.formatDistinctFrom(balance, TokenEntity.TON.symbol)
    is MigrationFeeShortage.Trx -> required.formatDistinctFrom(balance, TokenEntity.TRX.symbol)
    is MigrationFeeShortage.Both -> {
        "${requiredTon.formatDistinctFrom(balanceTon, TokenEntity.TON.symbol)} + " +
            requiredTrx.formatDistinctFrom(balanceTrx, TokenEntity.TRX.symbol)
    }
    is MigrationFeeShortage.Battery -> requiredCharges.toString()
}

fun MigrationFeeShortage.formatBalance(): String = when (this) {
    is MigrationFeeShortage.Ton -> balance.formatDistinctFrom(required, TokenEntity.TON.symbol)
    is MigrationFeeShortage.Trx -> balance.formatDistinctFrom(required, TokenEntity.TRX.symbol)
    is MigrationFeeShortage.Both -> {
        "${balanceTon.formatDistinctFrom(requiredTon, TokenEntity.TON.symbol)} + " +
            balanceTrx.formatDistinctFrom(requiredTrx, TokenEntity.TRX.symbol)
    }
    is MigrationFeeShortage.Battery -> availableCharges.toString()
}

private fun Coins.formatDistinctFrom(other: Coins, symbol: String): String {
    val short = formatWithSymbol(symbol)
    return if (compareTo(other) != 0 && short == other.formatWithSymbol(symbol)) {
        formatFullWithSymbol(symbol)
    } else {
        short
    }
}
