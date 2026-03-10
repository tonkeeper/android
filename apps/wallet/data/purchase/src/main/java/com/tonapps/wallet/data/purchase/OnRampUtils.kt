package com.tonapps.wallet.data.purchase

import com.tonapps.wallet.data.core.currency.WalletCurrency
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

object OnRampUtils {

    fun normalizeType(currency: WalletCurrency): String? {
        if (currency.fiat) {
            return null
        } else if (3 >= currency.address.length) {
            return "native"
        }
        return when (currency.chain.name.lowercase()) {
            "etc", "erc-20" -> "erc-20"
            "ton", "jetton" -> "jetton"
            "tron", "trc-20" -> "trc-20"
            "sol", "spl" -> "spl"
            "bnb", "bep-20" -> "bep-20"
            "avalanche" -> "avalanche"
            "arbitrum" -> "arbitrum"
            else -> null
        }
    }

    fun smartRoundUp(value: Double): Double {
        if (value <= 0.0) return value
        val mag = 10.0.pow(floor(log10(value)))
        val ticks = doubleArrayOf(1.0, 1.5, 2.0, 5.0)
        for (t in ticks) {
            val target = t * mag
            if (value <= target + 1e-12) {
                return if (abs(value - target) < 1e-9) value else target
            }
        }
        return 10.0 * mag
    }
}