package com.tonapps.wallet.data.multichain.exchange

/**
 * Slippage options the wallet offers for a given source chain, fetched from the aggregator config.
 *
 * @param options selectable values (ascending), each carrying its bps value and a display label.
 * @param defaultBps the pre-selected value in basis points (100 bps = 1%).
 */
data class SwapSlippage(
    val options: List<Option>,
    val defaultBps: Int,
) {
    data class Option(
        val bps: Int,
        val formatted: String,
    )
}
