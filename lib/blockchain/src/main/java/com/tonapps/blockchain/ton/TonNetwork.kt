package com.tonapps.blockchain.ton

enum class TonNetwork(
    val value: Int
) {
    MAINNET(-239),
    TESTNET(-3),
    TETRA(662387);

    val isMainnet: Boolean
        get() = this == MAINNET

    val isTestnet: Boolean
        get() = this == TESTNET

    val isTetra: Boolean
        get() = this == TETRA

    companion object {

        fun from(value: Int?): TonNetwork? {
            return when (value) {
                MAINNET.value -> MAINNET
                TESTNET.value -> TESTNET
                TETRA.value -> TETRA
                else -> null
            }
        }
    }
}
