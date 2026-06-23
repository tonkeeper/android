package com.tonapps.wallet.data.battery

import com.tonapps.icu.Coins
import com.tonapps.wallet.data.battery.entity.RechargeMethodEntity
import java.math.BigDecimal
import java.math.RoundingMode

object BatteryMapper {

    fun convertToCharges(
        balance: Coins,
        meanFees: String
    ): Int {
        if (!balance.isPositive) {
            return 0
        }
        val meanFeesBigDecimal = BigDecimal(meanFees)
        if (BigDecimal.ZERO >= meanFeesBigDecimal) {
            return 0
        }
        return balance.value.divide(meanFeesBigDecimal, 0, RoundingMode.UP).toInt()
    }

    fun convertFromCharges(
        charges: Int,
        meanFees: String
    ): Coins {
        val meanFeesBigDecimal = BigDecimal(meanFees)
        val amountBigDecimal = meanFeesBigDecimal.multiply(BigDecimal(charges))
        return Coins(amountBigDecimal)
    }

    fun calculateChargesAmount(
        transactionCostBigDecimal: BigDecimal,
        meanFees: String
    ): Int {
        val meanFeesBigDecimal = BigDecimal(meanFees)
        return transactionCostBigDecimal.divide(meanFeesBigDecimal, 0, RoundingMode.HALF_UP)
            .toInt()
    }

    fun calculateChargesAmount(
        transactionCost: String,
        meanFees: String
    ) = calculateChargesAmount(BigDecimal(transactionCost), meanFees)

    fun calculateCryptoCharges(method: RechargeMethodEntity, meanFees: String, amount: Coins): Int {
        val meanFeesBigDecimal = BigDecimal(meanFees)
        val rateBigDecimal = BigDecimal(method.rate)
        return rateBigDecimal.divide(meanFeesBigDecimal, 20, RoundingMode.HALF_UP)
            .multiply(amount.value).setScale(0, RoundingMode.FLOOR).toInt()
    }

    fun calculateIapCharges(
        userProceed: Double,
        tonPriceInUsd: Coins,
        reservedAmount: BigDecimal,
        meanFees: BigDecimal
    ): Int {
        if (meanFees <= BigDecimal.ZERO || tonPriceInUsd <= Coins.ZERO) {
            return 0
        }

        return userProceed.toBigDecimal()
            .divide(tonPriceInUsd.value, 20, RoundingMode.HALF_UP)
            .minus(reservedAmount)
            .divide(meanFees, 20, RoundingMode.HALF_UP)
            .setScale(0, RoundingMode.FLOOR)
            .toInt()
    }
}