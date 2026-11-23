package com.tonapps.wallet.data.battery

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/**
 * The charges a purchase in another asset adds, told from the charges one whole unit of the asset buys: the
 * method's `charges_rate`, after the markup. Before the network fee and without a promo's bonus: what
 * `/purchase-battery/estimate` answers replaces it.
 */
object BatteryRechargeMath {

    private const val DIVISION_SCALE = 20

    /**
     * The charges one whole unit of the asset buys: [chargesRate] as the battery sends it, or, from a battery
     * that sends none, [rate] (TON for one unit) over [chargeCost] (TON for one charge). Null when neither
     * gives a positive number.
     */
    fun chargesRate(chargesRate: String?, rate: String, chargeCost: String): BigDecimal? {
        val sent = chargesRate?.toBigDecimalOrNull()
        if (sent != null && sent.signum() > 0) {
            return sent
        }

        val cost = chargeCost.toBigDecimalOrNull() ?: return null
        val tonPerUnit = rate.toBigDecimalOrNull() ?: return null
        if (cost.signum() <= 0 || tonPerUnit.signum() <= 0) {
            return null
        }

        return tonPerUnit.divide(cost, DIVISION_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * The charges [amount] whole units of the asset buy, rounded down: never more than the purchase adds.
     * Capped at [Long.MAX_VALUE] rather than wrapped.
     */
    fun charges(amount: BigDecimal, chargesRate: BigDecimal?): Long {
        if (chargesRate == null || chargesRate.signum() <= 0 || amount.signum() <= 0) {
            return 0
        }

        val charges = amount.multiply(chargesRate).setScale(0, RoundingMode.FLOOR)
        return charges.min(BigDecimal.valueOf(Long.MAX_VALUE)).toLong()
    }

    /**
     * The whole units of the asset that buy [charges], rounded up to its [decimals]: a pack bought for it
     * holds at least as many charges as it says. Zero without a positive [chargesRate].
     */
    fun amount(charges: Int, chargesRate: BigDecimal?, decimals: Int): BigDecimal {
        if (chargesRate == null || chargesRate.signum() <= 0 || charges <= 0) {
            return BigDecimal.ZERO
        }

        return BigDecimal(charges).divide(chargesRate, decimals.coerceAtLeast(0), RoundingMode.CEILING)
    }

    /** Whether [amount] whole units reach the method's least purchase; a method without one takes any. */
    fun isEnough(amount: BigDecimal, minBootstrapValue: String?): Boolean {
        val min = minBootstrapValue?.toBigDecimalOrNull() ?: return amount.signum() > 0
        return amount.signum() > 0 && amount >= min
    }

    /** [amount] whole units in the asset's smallest ones, as estimate and emulate take it; what is below one is dropped. */
    fun toBaseUnits(amount: BigDecimal, decimals: Int): BigInteger {
        return amount.movePointRight(decimals).setScale(0, RoundingMode.DOWN).toBigIntegerExact()
    }
}
