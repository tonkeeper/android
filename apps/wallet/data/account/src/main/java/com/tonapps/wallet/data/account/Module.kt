package com.tonapps.wallet.data.account

import com.tonapps.wallet.data.account.n.AccountRepository
import org.koin.dsl.module

val accountModule = module {
    single<AccountRepository>(createdAtStart = true) { AccountRepository(get(), get()) }
}