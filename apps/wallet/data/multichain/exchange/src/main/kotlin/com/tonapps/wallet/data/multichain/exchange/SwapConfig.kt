package com.tonapps.wallet.data.multichain.exchange

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.tonapps.chainkit.core.chain.model.account.Chain
import com.tonapps.chainkit.core.chain.model.num.Formatter

data class SwapConfig(
    val defaultPair: SwapDefaultPair,
    val slippagePerChain: Map<String, SwapSlippage>,
    val defaultSlippage: SwapSlippage = DefaultSlippage,
) {

    fun slippage(chain: Chain): SwapSlippage {
        return slippagePerChain[chain.slippageKey]
            ?: defaultSlippage
    }

    companion object {
        internal val DefaultSlippage = SwapSlippage(
            options = listOf(100, 300, 500).map { bps ->
                SwapSlippage.Option(bps = bps, formatted = formatSlippage(bps))
            },
            defaultBps = 300,
        )
    }
}

internal val Chain.slippageKey: String
    get() = network.type.id

private val SLIPPAGE_PERCENT_MODE = DecimalMode(
    decimalPrecision = 30L,
    roundingMode = RoundingMode.ROUND_HALF_CEILING,
    scale = 1L,
)

internal fun formatSlippage(bps: Int): String {
    val percent = BigDecimal.fromInt(bps)
        .divide(BigDecimal.fromInt(100), SLIPPAGE_PERCENT_MODE) // 100 bps = 1%

    return Formatter.formatPercent(percent)
}
