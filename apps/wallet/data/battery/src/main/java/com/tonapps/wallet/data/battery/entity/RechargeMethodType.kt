package com.tonapps.wallet.data.battery.entity

enum class RechargeMethodType {
    TON,
    JETTON,

    // On the chains other than TON, listed for a multichain wallet only.
    TRC20,
    ERC20,
    SPL,
    COIN;

    /** Bought as before: a transfer to the battery's fund receiver on TON. */
    val isTon: Boolean
        get() = this == TON || this == JETTON
}
