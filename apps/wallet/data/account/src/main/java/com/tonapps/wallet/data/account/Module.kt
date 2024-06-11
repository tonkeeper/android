package com.tonapps.wallet.data.account

import com.tonapps.extensions.isMainVersion
import com.tonapps.wallet.data.account.backport.RNWalletRepository
import com.tonapps.wallet.data.account.legacy.LegacyWalletRepository
import com.tonapps.wallet.data.account.repository.BaseWalletRepository
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val accountModule = module {
    single<BaseWalletRepository>(createdAtStart = true) {
        if (androidApplication().isMainVersion) {
            RNWalletRepository(get(), get(), get())
        } else {
            LegacyWalletRepository(get(), get(), get(), get())
        }
    }
}