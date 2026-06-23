package com.tonapps.wallet.api.core

import com.tonapps.blockchain.ton.TonNetwork

data class SourceAPI<A>(
    private val mainnetAPI: A,
    private val testnetAPI: A,
    private val tetraAPI: A,
) {

    fun get(network: TonNetwork): A {
        return when (network) {
            TonNetwork.TESTNET -> testnetAPI
            TonNetwork.MAINNET -> mainnetAPI
            TonNetwork.TETRA -> tetraAPI
        }
    }
}