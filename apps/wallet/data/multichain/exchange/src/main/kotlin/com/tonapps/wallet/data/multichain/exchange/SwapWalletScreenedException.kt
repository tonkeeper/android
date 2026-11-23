package com.tonapps.wallet.data.multichain.exchange

class SwapWalletScreenedException(cause: Throwable? = null) :
    IllegalStateException("Wallet rejected by the swap provider's compliance screening", cause)
