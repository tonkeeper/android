package com.tonapps.dapp

import com.tonapps.wc.WalletConnectManager
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val wcModule = module {
    singleOf(::WalletConnectManager)
}