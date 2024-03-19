package com.tonapps.wallet.data.account

import org.koin.dsl.module

val accountModule = module {
    single { WalletRepository(get(), get(), get()) }
}