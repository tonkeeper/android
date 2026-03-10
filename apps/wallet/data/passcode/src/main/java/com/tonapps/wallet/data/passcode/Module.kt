package com.tonapps.wallet.data.passcode

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val passcodeModule = module {
    singleOf(::PasscodeHelper)
    singleOf(::PasscodeManager)
}