package com.tonapps.wallet.data.passcode

import org.koin.dsl.module

val passcodeModule = module {
    single { PasscodeManager(get(), get(), get(), get()) }
}