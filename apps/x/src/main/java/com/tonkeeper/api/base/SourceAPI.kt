package com.tonkeeper.api.base

data class SourceAPI<A>(
    private val mainnetAPI: A,
    private val testnetAPI: A
) {

    fun get(testnet: Boolean): A {
        return if (testnet) testnetAPI else mainnetAPI
    }
}