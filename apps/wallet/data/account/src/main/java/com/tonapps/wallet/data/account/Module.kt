package com.tonapps.wallet.data.account

import org.koin.dsl.module

val accountModule = module {
    single(createdAtStart=true) { WalletRepository(get(), get(), get(), get()) }
}