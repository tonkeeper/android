package com.tonapps.wallet.data.multichain.exchange

/**
 * A prepared route the wallet will not sign: it has no swap form here, or it says something else
 * than the swap it summarises. Asking again gets the same kind of route, so it is not retried.
 */
class SwapRouteRefusedException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)
