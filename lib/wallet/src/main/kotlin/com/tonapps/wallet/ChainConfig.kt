package com.tonapps.wallet

import com.tonapps.chainkit.core.chain.model.account.Chain

object ChainConfig {

    fun isBatterySupported(chain: Chain): Boolean {
        return chain is Chain.Ton || chain is Chain.Tron
    }

    fun isWcSupported(chain: Chain): Boolean {
        return chain is Chain.Tron || chain is Chain.Evm
    }
}
