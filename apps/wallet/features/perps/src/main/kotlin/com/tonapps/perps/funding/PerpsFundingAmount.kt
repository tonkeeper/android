package com.tonapps.perps.funding

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

class PerpsFundingAmountException(message: String) : IllegalArgumentException(message)

object PerpsFundingAmount {

    const val USDC_DECIMALS = 6

    val GATEWAY_MINIMUM_USDC_UNITS: BigInteger = BigInteger.valueOf(1_000_000L)

    fun declaredAmount(units: BigInteger, decimals: Int): String {
        require(units.signum() >= 0) { "perps funding: negative amount $units" }
        if (decimals <= 0) {
            return units.toString()
        }
        return BigDecimal(units, decimals).stripTrailingZeros().toPlainString()
    }

    fun units(declaredAmount: String, decimals: Int): BigInteger {
        val parts = declaredAmount.split('.')
        if (declaredAmount.isEmpty() || parts.size > 2) {
            throw PerpsFundingAmountException("malformed amount $declaredAmount")
        }
        val integerPart = parts[0]
        val fraction = parts.getOrNull(1).orEmpty()
        if ((integerPart.isEmpty() && fraction.isEmpty()) || !integerPart.isAsciiDigits() || !fraction.isAsciiDigits()) {
            throw PerpsFundingAmountException("malformed amount $declaredAmount")
        }
        if (fraction.length > decimals) {
            throw PerpsFundingAmountException("amount $declaredAmount has more than $decimals decimals")
        }
        val padded = integerPart + fraction + "0".repeat(decimals - fraction.length)
        return BigInteger(padded.ifEmpty { "0" })
    }

    fun integerUnits(value: String): BigInteger {
        if (value.isEmpty() || !value.isAsciiDigits()) {
            throw PerpsFundingAmountException("malformed units $value")
        }
        return BigInteger(value)
    }

    fun toDecimal(units: BigInteger, decimals: Int): BigDecimal = BigDecimal(units, decimals)

    fun toUnits(amount: BigDecimal, decimals: Int, rounding: RoundingMode): BigInteger {
        return amount.movePointRight(decimals).setScale(0, rounding).toBigIntegerExact()
    }

    private fun String.isAsciiDigits(): Boolean = all { it in '0'..'9' }
}
