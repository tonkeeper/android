package com.tonapps.wallet.data.passcode

import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

// ForegroundActivityTracker comes from the app, which registers it on the Application.
val passcodeModule = module {
    singleOf(::PasscodeHelper)
    single { PasscodeManager(get(), get(), get(), get(), get(), get(), get(), get(), getOrNull()) }
    singleOf(::ForegroundWalletUnlocker) { bind<WalletUnlocker>() }
}
