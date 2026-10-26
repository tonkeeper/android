package com.tonapps.blockchain.ton

enum class TonNetwork(
    val value: Int
) {
    MAINNET(-239),
    TESTNET(-3);

    val isMainnet: Boolean
        get() = this == MAINNET

    val isTestnet: Boolean
        get() = this == TESTNET

    companion object {

        fun from(value: Int?): TonNetwork? {
            return when (value) {
                MAINNET.value -> MAINNET
                TESTNET.value -> TESTNET
                else -> null
            }
        }
    }
}
