package com.tonapps.perps.trade

import com.tonapps.chainkit.lighter.LighterConstants
import com.tonapps.chainkit.perpetual.PerpsFreshnessPolicy
import com.tonapps.perps.data.MIN_LEVERAGE
import com.tonapps.wallet.data.multichain.wallet.CredentialIds
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.max

internal object PerpsProductRules {

    const val VENUE = "lighter"
    const val QUOTE_ASSET = "USDC"
    const val PRICE_SLIPPAGE_PPM = 50_000L
    const val TPSL_PRICE_SLIPPAGE_PPM = 30_000L
    const val TPSL_BUFFER_PPM = 2_000L
    const val MIN_ADJUST_MARGIN_QUOTE = "1"
    const val MIN_OPEN_MARGIN_QUOTE = "1"

    // Kept above the initial margin when removing margin, so "Max" isn't rejected on a small mark
    // move. Same as iOS.
    const val REMOVE_MARGIN_BUFFER_PPM = 100L
    const val MIN_REMOVE_MARGIN_BUFFER_QUOTE = "0.01"

    const val IMF_TICKS = 10_000
    const val DEFAULT_LEVERAGE = 20
    const val AUTO_CLOSE_EXPIRY_MS = 28L * 24 * 60 * 60 * 1000
    const val ENVIRONMENT = CredentialIds.LIGHTER_MAINNET

    const val CHAIN_ID = LighterConstants.ChainId

    private const val PERCENT_TO_PPM_SHIFT = 4

    val POLICY = PerpsFreshnessPolicy(
        maxRulesAgeUnixMs = 60_000,
        maxAccountSnapshotAgeUnixMs = 60_000,
        maxMarketSnapshotAgeUnixMs = 60_000,
        maxMarkAgeUnixMs = 60_000,
        maxTriggerMarkAgeUnixMs = 15_000,
        maxFutureSkewUnixMs = 5_000,
        planLifetimeUnixMs = 120_000,
    )

    fun defaultLeverage(maxLeverage: Int): Int {
        val ceiling = max(maxLeverage, MIN_LEVERAGE)

        return DEFAULT_LEVERAGE.coerceAtMost(ceiling)
    }

    fun leverageCeiling(published: Int?, initialMarginBps: Int?): Int {
        val derived = initialMarginBps?.takeIf { it > 0 }?.let { IMF_TICKS / it }

        return (published ?: derived ?: MIN_LEVERAGE).coerceAtLeast(MIN_LEVERAGE)
    }

    // Rounding down would hand back more leverage than asked and move the liquidation price closer.
    fun initialMarginBps(leverage: Int): Int {
        val bps = BigDecimal(IMF_TICKS)
            .divide(BigDecimal(max(leverage, 1)), 0, RoundingMode.CEILING)
            .toInt()

        return bps.coerceIn(1, IMF_TICKS)
    }

    fun leverage(initialMarginBps: Int): Int {
        if (initialMarginBps <= 0) {
            return 0
        }

        return (IMF_TICKS + initialMarginBps - 1) / initialMarginBps
    }

    fun percentToPpm(percent: BigDecimal): Long {
        return percent.movePointRight(PERCENT_TO_PPM_SHIFT).setScale(0, RoundingMode.CEILING).toLong()
    }
}
