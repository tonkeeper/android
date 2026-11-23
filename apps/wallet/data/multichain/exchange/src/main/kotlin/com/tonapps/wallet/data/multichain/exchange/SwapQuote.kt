package com.tonapps.wallet.data.multichain.exchange

import com.ionspin.kotlin.bignum.integer.BigInteger
import io.exchangeapi.models.CrossSwapPayload

data class SwapQuote(
    val routeId: String,
    val sourceBaseAmount: BigInteger,
    val buyBaseAmount: BigInteger,
    val minimumBuyBaseAmount: BigInteger,
    val slippageBps: Int?,
    val priceImpactBps: Int?,
    val provider: Provider,
    val providerTxId: String? = null,
    val payloads: List<CrossSwapPayload>? = null,
    val estimatedSeconds: Int? = null,
    val expiresAtMillis: Long? = null,
    val receivedAt: Long? = null,
    val lifetimeMillis: Long? = null,
    /** Quoted on `/v3/crosschain`: the route is prepared there too, as a Generic Payload operation. */
    val genericPayload: Boolean = false,
) {
    enum class Provider(val id: String) {
        Omniston("omniston"),
        SwapXyz("swapsxyz"),
        SwapKit("swapkit"),
        ;

        companion object {
            fun forceFrom(id: String): Provider {
                return entries.firstOrNull { it.id == id }
                    ?: throw IllegalArgumentException("Unknown swap provider: $id")
            }
        }
    }
}
