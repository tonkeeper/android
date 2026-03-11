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
}
